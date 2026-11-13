package io.bsp4k.client

import io.jsonrpc4k.core.BoundedDispatchPolicy
import io.jsonrpc4k.core.Connection
import io.jsonrpc4k.core.InboundResourceLimits
import io.jsonrpc4k.core.JsonRpcMessageDecoder
import io.jsonrpc4k.core.OutboundResourceLimits
import io.jsonrpc4k.core.RequestId
import io.jsonrpc4k.core.RequestMessage
import io.jsonrpc4k.transport.Transport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BoundedSessionTest {
    @Test
    fun `response is pumped while notification handler awaits a reverse call`() =
        runTest {
            val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
            val sibling = Job(parent)
            val scope = CoroutineScope(backgroundScope.coroutineContext + parent)
            val transport = BoundedClientTransport()
            val done = CompletableDeferred<Unit>()
            lateinit var session: BuildClientSession
            val config =
                buildClient {
                    onNotification("test/reverse") {
                        session.server.workspaceBuildTargets()
                        done.complete(Unit)
                    }
                }
            session = config.start(transport, policy(), inbound(), outbound(), scope)
            try {
                transport.incomingFrames.send(frame("""{"jsonrpc":"2.0","method":"test/reverse"}"""))
                withTimeout(5_000) { done.await() }
                assertTrue(parent.isActive)
                assertTrue(sibling.isActive)
                assertFalse(transport.closed)
            } finally {
                session.close()
                parent.cancel()
            }
        }

    @Test
    fun `legacy synchronous session keeps its existing reverse-call behavior`() =
        runTest {
            val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
            val scope = CoroutineScope(backgroundScope.coroutineContext + parent)
            val transport = BoundedClientTransport()
            val done = CompletableDeferred<Unit>()
            lateinit var session: BuildClientSession
            val config =
                buildClient {
                    onNotification("test/reverse") {
                        session.server.workspaceBuildTargets()
                        done.complete(Unit)
                    }
                }
            session = config.start(transport, scope)
            try {
                transport.incomingFrames.send(frame("""{"jsonrpc":"2.0","method":"test/reverse"}"""))
                runCurrent()
                assertFalse(done.isCompleted)
            } finally {
                session.close()
                parent.cancel()
            }
        }

    @Test
    fun `core frame failure terminates session with original cause and cleans pending call`() =
        runTest {
            val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
            val sibling = Job(parent)
            val scope = CoroutineScope(backgroundScope.coroutineContext + parent)
            val transport = BoundedClientTransport(respond = false)
            val session = buildClient {}.start(transport, policy(), inbound(), outbound(), scope)
            try {
                val pending = async { runCatching { session.server.workspaceBuildTargets() } }
                runCurrent()
                transport.incomingFrames.send("Content-Length: 4097\r\n\r\n".encodeToByteArray())
                val terminal = withTimeout(5_000) { session.awaitTermination() }
                assertTrue(terminal?.message.orEmpty().contains("Frame body exceeds 4096"))
                assertTrue(withTimeout(5_000) { pending.await() }.isFailure)
                assertTrue(parent.isActive)
                assertTrue(sibling.isActive)
                assertFalse(transport.closed)
            } finally {
                session.close()
                parent.cancel()
            }
        }

    @Test
    fun `large shallow notification is delivered within explicit limits`() =
        runTest {
            val transport = BoundedClientTransport()
            val received = CompletableDeferred<String>()
            val session =
                buildClient {
                    onNotification("test/large") { params ->
                        val value = assertIs<JsonPrimitive>(assertIs<JsonObject>(params)["text"])
                        received.complete(value.content)
                    }
                }.start(transport, policy(), inbound(), outbound(), backgroundScope)
            try {
                val text = "é".repeat(1500)
                val body = """{"jsonrpc":"2.0","method":"test/large","params":{"text":"$text"}}"""
                transport.incomingFrames.send(frame(body))
                assertEquals(text, withTimeout(5_000) { received.await() })
            } finally {
                session.close()
            }
        }

    @Test
    fun `parent cancellation closes bounded session without owning transport`() =
        runTest {
            val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
            val scope = CoroutineScope(backgroundScope.coroutineContext + parent)
            val transport = BoundedClientTransport(respond = false)
            val session = buildClient {}.start(transport, policy(), inbound(), outbound(), scope)
            parent.cancel()
            assertEquals(null, withTimeout(5_000) { session.awaitTermination() })
            assertFalse(transport.closed)
        }

    @Test
    fun `core failure survives parent cancellation before state observer runs`() =
        runTest {
            val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
            val scope = CoroutineScope(backgroundScope.coroutineContext + parent)
            val transport = BoundedClientTransport(respond = false)
            val lifecycle = SessionLifecycle(scope) { Connection(policy(), inbound(), outbound(), it) }
            lifecycle.start(transport)
            runCatching { lifecycle.connection.receive("Content-Length: 4097\r\n\r\n") }
            parent.cancel()
            val terminal = withTimeout(5_000) { lifecycle.awaitTermination() }
            assertTrue(terminal?.message.orEmpty().contains("Frame body exceeds 4096"))
            assertFalse(transport.closed)
        }

    @Test
    fun `pending caller close cannot erase an earlier core control failure`() =
        runTest {
            val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
            val sibling = Job(parent)
            val scope = CoroutineScope(backgroundScope.coroutineContext + parent)
            val transport = BoundedClientTransport(respond = false, blockWrites = true)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val session =
                buildClient {
                    onNotification("test/hold") {
                        entered.complete(Unit)
                        release.await()
                    }
                }.start(
                    transport,
                    BoundedDispatchPolicy(1, 2),
                    inbound(),
                    OutboundResourceLimits(1024, 1024, 1, 8, 1024, 1, 1024, 32),
                    scope,
                )
            try {
                val pending =
                    async(UnconfinedTestDispatcher(testScheduler)) {
                        try {
                            runCatching { session.server.workspaceBuildTargets() }
                        } finally {
                            session.close()
                        }
                    }
                withTimeout(5_000) { transport.writeStarted.await() }
                runCurrent()
                assertFalse(pending.isCompleted)
                transport.incomingFrames.send(frame("""{"jsonrpc":"2.0","method":"test/hold"}"""))
                withTimeout(5_000) { entered.await() }
                for (id in 1..4) {
                    transport.incomingFrames.send(frame("""{"jsonrpc":"2.0","id":$id,"method":"test/unknown"}"""))
                }
                val terminal = withTimeout(5_000) { session.awaitTermination() }
                assertTrue(terminal?.message.orEmpty().contains("control capacity"))
                assertTrue(withTimeout(5_000) { pending.await() }.isFailure)
                assertTrue(parent.isActive)
                assertTrue(sibling.isActive)
                assertFalse(transport.closed)
            } finally {
                release.complete(Unit)
                transport.releaseWrites.complete(Unit)
                session.close()
                parent.cancel()
            }
        }

    @Test
    fun `transport failure survives pending caller finally close`() =
        runTest {
            val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
            val sibling = Job(parent)
            val scope = CoroutineScope(backgroundScope.coroutineContext + parent)
            val failure = IllegalStateException("transport write failed")
            val transport = BoundedClientTransport(respond = false, blockWrites = true, writeFailure = failure)
            val session = buildClient {}.start(transport, policy(), inbound(), outbound(), scope)
            try {
                val pending =
                    async(UnconfinedTestDispatcher(testScheduler)) {
                        try {
                            runCatching { session.server.workspaceBuildTargets() }
                        } finally {
                            session.close()
                        }
                    }
                withTimeout(5_000) { transport.writeStarted.await() }
                runCurrent()
                assertFalse(pending.isCompleted)
                transport.releaseWrites.complete(Unit)
                val terminal = withTimeout(5_000) { session.awaitTermination() }
                assertTrue(generateSequence<Throwable>(terminal) { it.cause }.take(8).any { it === failure })
                assertTrue(withTimeout(5_000) { pending.await() }.isFailure)
                assertTrue(parent.isActive)
                assertTrue(sibling.isActive)
                assertFalse(transport.closed)
            } finally {
                session.close()
                parent.cancel()
            }
        }

    @Test
    fun `explicit close before later input remains normal`() =
        runTest {
            val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
            val scope = CoroutineScope(backgroundScope.coroutineContext + parent)
            val transport = BoundedClientTransport(respond = false)
            val session = buildClient {}.start(transport, policy(), inbound(), outbound(), scope)
            try {
                session.close()
                transport.incomingFrames.send("Content-Length: 4097\r\n\r\n".encodeToByteArray())
                assertEquals(null, withTimeout(5_000) { session.awaitTermination() })
                assertTrue(parent.isActive)
                assertFalse(transport.closed)
            } finally {
                parent.cancel()
            }
        }

    private fun policy() = BoundedDispatchPolicy(2, 4)

    private fun inbound() = InboundResourceLimits(4096, 32, 8192)

    private fun outbound() = OutboundResourceLimits(4096, 8192, 8, 8, 4096, 2, 1024, 32)
}

private class BoundedClientTransport(
    private val respond: Boolean = true,
    private val blockWrites: Boolean = false,
    private val writeFailure: Throwable? = null,
) : Transport {
    val incomingFrames = Channel<ByteArray>(Channel.UNLIMITED)
    override val incoming: Flow<ByteArray> = incomingFrames.receiveAsFlow()
    override val isConnected: Boolean = true
    var closed = false
    val writeStarted = CompletableDeferred<Unit>()
    val releaseWrites = CompletableDeferred<Unit>()

    override suspend fun send(data: ByteArray) {
        if (blockWrites) {
            writeStarted.complete(Unit)
            releaseWrites.await()
        }
        writeFailure?.let { throw it }
        if (!respond) return
        val request = JsonRpcMessageDecoder().feed(data).single() as RequestMessage
        // The response envelope is literal wire JSON; only the numeric ID is copied.
        val number = assertIs<RequestId.NumberId>(request.id).value
        incomingFrames.send(frame("""{"jsonrpc":"2.0","id":$number,"result":{"targets":[]}}"""))
    }

    override suspend fun close() {
        closed = true
    }
}

private fun frame(body: String): ByteArray {
    val bytes = body.encodeToByteArray()
    return "Content-Length: ${bytes.size}\r\n\r\n".encodeToByteArray() + bytes
}
