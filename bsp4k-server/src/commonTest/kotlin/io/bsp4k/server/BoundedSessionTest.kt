package io.bsp4k.server

import io.bsp4k.protocol.MessageType
import io.jsonrpc4k.core.BoundedDispatchPolicy
import io.jsonrpc4k.core.Connection
import io.jsonrpc4k.core.InboundResourceLimits
import io.jsonrpc4k.core.JsonRpcMessageDecoder
import io.jsonrpc4k.core.OutboundResourceLimits
import io.jsonrpc4k.core.ResponseMessage
import io.jsonrpc4k.transport.Transport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BoundedSessionTest {
    @Test
    fun `notification barrier waits for earlier request before later request`() =
        runTest {
            val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
            val sibling = Job(parent)
            val scope = CoroutineScope(backgroundScope.coroutineContext + parent)
            val transport = BoundedServerTransport()
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val order = mutableListOf<String>()
            val config =
                buildServer {
                    onRequest("test/hold") {
                        order.add("request-start")
                        started.complete(Unit)
                        release.await()
                        order.add("request-end")
                        null
                    }
                    onNotification("test/notice") { order.add("notification") }
                    onRequest("test/later") {
                        order.add("later")
                        null
                    }
                }
            val server = config.start(transport, policy(), inbound(), outbound(), scope)
            try {
                initialize(transport)
                transport.incomingFrames.send(frame("""{"jsonrpc":"2.0","id":2,"method":"test/hold"}"""))
                withTimeout(5_000) { started.await() }
                transport.incomingFrames.send(frame("""{"jsonrpc":"2.0","method":"test/notice"}"""))
                transport.incomingFrames.send(frame("""{"jsonrpc":"2.0","id":3,"method":"test/later"}"""))
                runCurrent()
                assertEquals(listOf("request-start"), order)
                release.complete(Unit)
                withTimeout(5_000) {
                    transport.outgoingFrames.receive()
                    transport.outgoingFrames.receive()
                }
                assertEquals(listOf("request-start", "request-end", "notification", "later"), order)
                assertTrue(parent.isActive)
                assertTrue(sibling.isActive)
                assertFalse(transport.closed)
            } finally {
                server.close()
                parent.cancel()
            }
        }

    @Test
    fun `oversized handler response fails session without closing borrowed transport`() =
        runTest {
            val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
            val sibling = Job(parent)
            val scope = CoroutineScope(backgroundScope.coroutineContext + parent)
            val transport = BoundedServerTransport()
            val server =
                buildServer {
                    onRequest("test/large") { JsonPrimitive("a".repeat(2048)) }
                }.start(transport, policy(), inbound(), outbound(), scope)
            try {
                initialize(transport)
                transport.incomingFrames.send(frame("""{"jsonrpc":"2.0","id":2,"method":"test/large"}"""))
                val failure = withTimeout(5_000) { server.awaitTermination() }
                assertTrue(failure?.message.orEmpty().contains("exceeds"))
                assertTrue(parent.isActive)
                assertTrue(sibling.isActive)
                assertFalse(transport.closed)
            } finally {
                server.close()
                parent.cancel()
            }
        }

    @Test
    fun `control saturation propagates core failure through public server`() =
        runTest {
            val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
            val sibling = Job(parent)
            val scope = CoroutineScope(backgroundScope.coroutineContext + parent)
            val transport = BoundedServerTransport()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val server =
                buildServer {
                    onRequest("test/hold") {
                        entered.complete(Unit)
                        release.await()
                        null
                    }
                }.start(
                    transport,
                    BoundedDispatchPolicy(1, 2),
                    inbound(),
                    OutboundResourceLimits(1024, 1024, 1, 8, 1024, 1, 1024, 32),
                    scope,
                )
            try {
                initialize(transport)
                transport.blockWrites = true
                server.client.logMessage(MessageType.Info, "held")
                withTimeout(5_000) { transport.writeStarted.await() }
                transport.incomingFrames.send(frame("""{"jsonrpc":"2.0","id":2,"method":"test/hold"}"""))
                withTimeout(5_000) { entered.await() }
                transport.incomingFrames.send(frame("""{"jsonrpc":"2.0","id":3,"method":"test/hold"}"""))
                runCurrent()
                transport.incomingFrames.send(frame("""{"jsonrpc":"2.0","id":4,"method":"test/hold"}"""))
                transport.incomingFrames.send(frame("""{"jsonrpc":"2.0","id":5,"method":"test/hold"}"""))
                transport.incomingFrames.send(frame("""{"jsonrpc":"2.0","id":6,"method":"test/hold"}"""))
                val failure = withTimeout(5_000) { server.awaitTermination() }
                assertTrue(failure?.message.orEmpty().contains("control capacity"))
                assertTrue(parent.isActive)
                assertTrue(sibling.isActive)
                assertFalse(transport.closed)
            } finally {
                release.complete(Unit)
                transport.releaseWrites.complete(Unit)
                server.close()
                parent.cancel()
            }
        }

    private suspend fun initialize(transport: BoundedServerTransport) {
        val body =
            """{"jsonrpc":"2.0","id":1,"method":"build/initialize","params":{"displayName":"test","version":"1",""" +
                """"bspVersion":"2.1.0","rootUri":"file:///fixture","capabilities":{"languageIds":[]}}}"""
        transport.incomingFrames.send(frame(body))
        val response = withTimeout(5_000) { transport.outgoingFrames.receive() }
        assertIs<ResponseMessage>(JsonRpcMessageDecoder().feedMessages(response).single())
        transport.incomingFrames.send(frame("""{"jsonrpc":"2.0","method":"build/initialized"}"""))
    }

    @Test
    fun `core failure survives parent cancellation before state observer runs`() =
        runTest {
            val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
            val scope = CoroutineScope(backgroundScope.coroutineContext + parent)
            val transport = BoundedServerTransport()
            val lifecycle = SessionLifecycle(scope) { Connection(policy(), inbound(), outbound(), it) }
            lifecycle.start(transport)
            runCatching { lifecycle.connection.receive("Content-Length: 4097\r\n\r\n") }
            parent.cancel()
            val terminal = withTimeout(5_000) { lifecycle.awaitTermination() }
            assertTrue(terminal?.message.orEmpty().contains("Frame body exceeds 4096"))
            assertFalse(transport.closed)
        }

    private fun policy() = BoundedDispatchPolicy(2, 8)

    private fun inbound() = InboundResourceLimits(4096, 32, 8192)

    private fun outbound() = OutboundResourceLimits(1024, 2048, 8, 8, 1024, 2, 1024, 32)
}

private class BoundedServerTransport : Transport {
    val incomingFrames = Channel<ByteArray>(Channel.UNLIMITED)
    val outgoingFrames = Channel<ByteArray>(Channel.UNLIMITED)
    override val incoming: Flow<ByteArray> = incomingFrames.receiveAsFlow()
    override val isConnected: Boolean = true
    var closed = false
    var blockWrites = false
    val writeStarted = CompletableDeferred<Unit>()
    val releaseWrites = CompletableDeferred<Unit>()

    override suspend fun send(data: ByteArray) {
        if (blockWrites) {
            writeStarted.complete(Unit)
            releaseWrites.await()
        }
        outgoingFrames.send(data)
    }

    override suspend fun close() {
        closed = true
    }
}

private fun frame(body: String): ByteArray {
    val bytes = body.encodeToByteArray()
    return "Content-Length: ${bytes.size}\r\n\r\n".encodeToByteArray() + bytes
}
