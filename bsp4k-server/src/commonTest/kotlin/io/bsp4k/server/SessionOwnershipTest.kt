package io.bsp4k.server

import io.jsonrpc4k.core.JsonRpcCodec
import io.jsonrpc4k.core.JsonRpcMessageDecoder
import io.jsonrpc4k.core.NotificationMessage
import io.jsonrpc4k.core.RequestId
import io.jsonrpc4k.core.RequestMessage
import io.jsonrpc4k.core.ResponseError
import io.jsonrpc4k.core.ResponseMessage
import io.jsonrpc4k.transport.Transport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SessionOwnershipTest {
    @Test
    fun `exit stops the reader without canceling the supplied scope or transport`() =
        runTest {
            val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
            val sibling = Job(parent)
            val scope = CoroutineScope(backgroundScope.coroutineContext + parent)
            val transport = ExitTransport()
            buildServer {}.start(transport, scope)
            runCurrent()

            transport.messages.send(JsonRpcCodec.Default.encode(NotificationMessage("build/exit")))
            runCurrent()

            assertTrue(parent.isActive)
            assertTrue(sibling.isActive)
            assertTrue(transport.readerStopped.isCompleted)
            assertFalse(transport.closed)
            parent.cancel()
        }

    @Test
    fun `explicit close stops server without canceling caller or transport`() =
        runTest {
            val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
            val sibling = Job(parent)
            val scope = CoroutineScope(backgroundScope.coroutineContext + parent)
            val transport = ExitTransport()
            val server = buildServer {}.start(transport, scope)
            runCurrent()
            server.close()
            assertEquals(null, withTimeout(5_000) { server.awaitTermination() })
            assertTrue(transport.readerStopped.isCompleted)
            assertTrue(parent.isActive)
            assertTrue(sibling.isActive)
            assertFalse(transport.closed)
            parent.cancel()
        }

    @Test
    fun `parent cancellation stops server reader`() =
        runTest {
            val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
            val scope = CoroutineScope(backgroundScope.coroutineContext + parent)
            val transport = ExitTransport()
            val server = buildServer {}.start(transport, scope)
            runCurrent()
            parent.cancel()
            assertEquals(null, withTimeout(5_000) { server.awaitTermination() })
            assertTrue(transport.readerStopped.isCompleted)
            assertFalse(transport.closed)
        }

    @Test
    fun `already canceled parent returns a terminated server`() =
        runTest {
            val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
            parent.cancel()
            val scope = CoroutineScope(backgroundScope.coroutineContext + parent)
            val transport = ExitTransport()
            val server = buildServer {}.start(transport, scope)
            assertEquals(null, withTimeout(5_000) { server.awaitTermination() })
            assertFalse(transport.closed)
        }

    @Test
    fun `transport cancellation failure ends the server without canceling parent`() =
        runTest {
            val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
            val scope = CoroutineScope(backgroundScope.coroutineContext + parent)
            val failure = CancellationException("read stopped")
            val transport =
                object : Transport {
                    override val incoming: Flow<ByteArray> = flow { throw failure }
                    override val isConnected: Boolean = true

                    override suspend fun send(data: ByteArray) = Unit

                    override suspend fun close() = Unit
                }
            val server = buildServer {}.start(transport, scope)
            val terminal = withTimeout(5_000) { server.awaitTermination() }
            assertTrue(terminal is CancellationException)
            assertTrue(generateSequence<Throwable>(terminal) { it.cause }.take(8).any { it === failure })
            assertTrue(parent.isActive)
            parent.cancel()
        }

    @Test
    fun `EOF stops server jobs and preserves caller scope`() =
        runTest {
            val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
            val sibling = Job(parent)
            val scope = CoroutineScope(backgroundScope.coroutineContext + parent)
            val transport = ExitTransport()
            val server = buildServer {}.start(transport, scope)
            runCurrent()
            transport.messages.close()
            assertEquals(null, withTimeout(5_000) { server.awaitTermination() })
            assertTrue(transport.readerStopped.isCompleted)
            assertTrue(parent.isActive)
            assertTrue(sibling.isActive)
            assertFalse(transport.closed)
            parent.cancel()
        }

    @Test
    fun `response send failure ends the server and retains cause`() =
        runTest {
            val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
            val sibling = Job(parent)
            val scope = CoroutineScope(backgroundScope.coroutineContext + parent)
            val failure = IllegalStateException("server send failed")
            val messages = Channel<ByteArray>(Channel.UNLIMITED)
            var closed = false
            val transport =
                object : Transport {
                    override val incoming: Flow<ByteArray> = flow { for (message in messages) emit(message) }
                    override val isConnected: Boolean = true

                    override suspend fun send(data: ByteArray): Unit = throw failure

                    override suspend fun close() {
                        closed = true
                    }
                }
            val server = buildServer {}.start(transport, scope)
            messages.send(JsonRpcCodec.Default.encode(RequestMessage(RequestId.of(8), "build/shutdown")))
            val terminal = withTimeout(5_000) { server.awaitTermination() }
            assertTrue(terminal is IllegalStateException)
            assertTrue(generateSequence<Throwable>(terminal) { it.cause }.take(8).any { it === failure })
            assertTrue(parent.isActive)
            assertTrue(sibling.isActive)
            assertFalse(closed)
            parent.cancel()
        }

    @Test
    fun `pipelined exit retains response send failure while flushing`() =
        runTest {
            val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
            val scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler) + parent)
            val failure = IllegalStateException("shutdown response send failed")
            val transport = ExitTransport(failure)
            val server = buildServer {}.start(transport, scope)
            val shutdown = JsonRpcCodec.Default.encode(RequestMessage(RequestId.of(9), "build/shutdown"))
            val exit = JsonRpcCodec.Default.encode(NotificationMessage("build/exit"))
            transport.messages.send(shutdown + exit)
            runCurrent()
            assertTrue(transport.sendStarted.isCompleted)
            transport.releaseSend.complete(Unit)
            val terminal = withTimeout(5_000) { server.awaitTermination() }
            assertTrue(generateSequence<Throwable>(terminal) { it.cause }.take(8).any { it === failure })
            assertTrue(parent.isActive)
            assertFalse(transport.closed)
            parent.cancel()
        }

    @Test
    fun `pipelined exit waits for shutdown response to be sent`() =
        runTest {
            val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
            val sibling = Job(parent)
            val scope = CoroutineScope(backgroundScope.coroutineContext + parent)
            val transport = ExitTransport()
            val server = buildServer {}.start(transport, scope)
            val shutdown = JsonRpcCodec.Default.encode(RequestMessage(RequestId.of(7), "build/shutdown"))
            val exit = JsonRpcCodec.Default.encode(NotificationMessage("build/exit"))
            val terminal = async { withTimeout(5_000) { server.awaitTermination() } }
            transport.messages.send(shutdown + exit)
            runCurrent()

            assertTrue(transport.sendStarted.isCompleted)
            assertFalse(terminal.isCompleted)
            transport.releaseSend.complete(Unit)
            assertEquals(null, withTimeout(5_000) { terminal.await() })
            val response = JsonRpcMessageDecoder().feed(transport.sent.single()).single() as ResponseMessage
            assertEquals(RequestId.of(7), response.id)
            // Shutdown before initialize is refused, and exit must still flush that answer before closing.
            assertEquals(ResponseError.SERVER_NOT_INITIALIZED, response.error?.code)
            assertTrue(parent.isActive)
            assertTrue(sibling.isActive)
            assertFalse(transport.closed)
            parent.cancel()
        }
}

private class ExitTransport(
    private val failure: Throwable? = null,
) : Transport {
    val messages = Channel<ByteArray>(Channel.UNLIMITED)
    val readerStopped = CompletableDeferred<Unit>()
    val sendStarted = CompletableDeferred<Unit>()
    val releaseSend = CompletableDeferred<Unit>()
    val sent = mutableListOf<ByteArray>()
    var closed = false
    override val isConnected: Boolean = true
    override val incoming: Flow<ByteArray> =
        flow {
            try {
                for (message in messages) emit(message)
            } finally {
                readerStopped.complete(Unit)
            }
        }

    override suspend fun send(data: ByteArray) {
        sendStarted.complete(Unit)
        releaseSend.await()
        failure?.let { throw it }
        sent.add(data)
    }

    override suspend fun close() {
        closed = true
    }
}
