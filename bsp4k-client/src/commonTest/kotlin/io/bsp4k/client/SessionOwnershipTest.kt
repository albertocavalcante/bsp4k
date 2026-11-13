package io.bsp4k.client

import io.jsonrpc4k.core.JsonRpcCodec
import io.jsonrpc4k.core.JsonRpcException
import io.jsonrpc4k.core.JsonRpcMessageDecoder
import io.jsonrpc4k.core.NotificationMessage
import io.jsonrpc4k.core.RequestMessage
import io.jsonrpc4k.core.ResponseError
import io.jsonrpc4k.core.ResponseMessage
import io.jsonrpc4k.transport.Transport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SessionOwnershipTest {
    @Test
    fun `close stops the idle reader without canceling the supplied scope or transport`() =
        runTest {
            val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
            val sibling = Job(parent)
            val scope = CoroutineScope(backgroundScope.coroutineContext + parent)
            val transport = IdleTransport()
            val session = buildClient {}.start(transport, scope)
            runCurrent()

            session.close()
            runCurrent()

            assertTrue(parent.isActive)
            assertTrue(sibling.isActive)
            assertTrue(transport.readerStopped.isCompleted)
            assertFalse(transport.closed)
            parent.cancel()
        }

    @Test
    fun `canceling the parent stops its session reader`() =
        runTest {
            val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
            val scope = CoroutineScope(backgroundScope.coroutineContext + parent)
            val transport = IdleTransport()
            val session = buildClient {}.start(transport, scope)
            runCurrent()
            parent.cancel()
            runCurrent()
            assertTrue(transport.readerStopped.isCompleted)
            withTimeout(5_000) { session.awaitTermination() }
            assertFalse(transport.closed)
        }

    @Test
    fun `already canceled parent returns a terminated session`() =
        runTest {
            val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
            parent.cancel()
            val scope = CoroutineScope(backgroundScope.coroutineContext + parent)
            val transport = IdleTransport()
            val session = buildClient {}.start(transport, scope)
            assertEquals(null, withTimeout(5_000) { session.awaitTermination() })
            assertFalse(transport.closed)
        }

    @Test
    fun `canceling a termination waiter does not change the shared result`() =
        runTest {
            val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
            val scope = CoroutineScope(backgroundScope.coroutineContext + parent)
            val session = buildClient {}.start(IdleTransport(), scope)
            val waiter = async { withTimeout(5_000) { session.awaitTermination() } }
            runCurrent()
            waiter.cancelAndJoin()
            session.close()
            assertEquals(null, withTimeout(5_000) { session.awaitTermination() })
            assertTrue(parent.isActive)
            parent.cancel()
        }

    @Test
    fun `EOF stops pending requests and preserves the borrowed scope`() =
        runTest {
            val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
            val sibling = Job(parent)
            val scope = CoroutineScope(backgroundScope.coroutineContext + parent)
            val transport = ScriptedTransport()
            val session = buildClient {}.start(transport, scope)
            val pending = async { runCatching { session.assumeInitialized().server.workspaceBuildTargets() } }
            runCurrent()
            transport.messages.close()
            runCurrent()

            assertTrue(withTimeout(5_000) { pending.await() }.isFailure)
            assertEquals(null, withTimeout(5_000) { session.awaitTermination() })
            assertTrue(parent.isActive)
            assertTrue(sibling.isActive)
            assertFalse(transport.closed)
            parent.cancel()
        }

    @Test
    fun `send failure terminates pending request and exposes the original cause`() =
        runTest {
            val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
            val sibling = Job(parent)
            val scope = CoroutineScope(backgroundScope.coroutineContext + parent)
            val failure = IllegalStateException("send failure")
            val transport = ScriptedTransport(failure = failure)
            val session = buildClient {}.start(transport, scope)
            val pending = async { runCatching { session.assumeInitialized().server.workspaceBuildTargets() } }
            runCurrent()

            assertTrue(withTimeout(5_000) { pending.await() }.isFailure)
            val terminal = withTimeout(5_000) { session.awaitTermination() }
            assertTrue(terminal is IllegalStateException)
            assertTrue(generateSequence<Throwable>(terminal) { it.cause }.take(8).any { it === failure })
            session.close()
            assertEquals(terminal, withTimeout(5_000) { session.awaitTermination() })
            assertTrue(parent.isActive)
            assertTrue(sibling.isActive)
            assertFalse(transport.closed)
            parent.cancel()
        }

    @Test
    fun `fatal send failure is reported after session cleanup`() =
        runTest {
            val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
            val reported = CompletableDeferred<Throwable>()
            val handler = CoroutineExceptionHandler { _, error -> reported.complete(error) }
            val scope = CoroutineScope(backgroundScope.coroutineContext + handler + parent)
            val failure = AssertionError("fatal transport failure")
            val session = buildClient {}.start(ScriptedTransport(failure = failure), scope)
            val pending = async { runCatching { session.assumeInitialized().server.workspaceBuildTargets() } }
            runCurrent()
            assertTrue(withTimeout(5_000) { pending.await() }.isFailure)
            val terminal = withTimeout(5_000) { session.awaitTermination() }
            assertTrue(generateSequence<Throwable>(terminal) { it.cause }.take(8).any { it === failure })
            val observed = withTimeout(5_000) { reported.await() }
            assertTrue(generateSequence<Throwable>(observed) { it.cause }.take(8).any { it === failure })
            assertTrue(parent.isActive)
            parent.cancel()
        }

    @Test
    fun `transport cancellation failure terminates session without canceling parent`() =
        runTest {
            val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
            val scope = CoroutineScope(backgroundScope.coroutineContext + parent)
            val failure = CancellationException("transport stopped")
            val transport = ScriptedTransport(failure = failure)
            val session = buildClient {}.start(transport, scope)
            val pending = async { runCatching { session.assumeInitialized().server.workspaceBuildTargets() } }
            runCurrent()
            assertTrue(withTimeout(5_000) { pending.await() }.isFailure)
            val terminal = withTimeout(5_000) { session.awaitTermination() }
            assertTrue(terminal is CancellationException)
            assertTrue(generateSequence<Throwable>(terminal) { it.cause }.take(8).any { it === failure })
            assertTrue(parent.isActive)
            parent.cancel()
        }

    @Test
    fun `canceling blocked shutdown stops session without closing borrowed transport`() =
        runTest {
            val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
            val scope = CoroutineScope(backgroundScope.coroutineContext + parent)
            val transport = ScriptedTransport()
            val session = buildClient {}.start(transport, scope)
            val shutdown = async { session.shutdown() }
            runCurrent()
            assertTrue(transport.exitStarted.isCompleted)
            shutdown.cancelAndJoin()
            assertEquals(null, withTimeout(5_000) { session.awaitTermination() })
            assertTrue(parent.isActive)
            assertFalse(transport.closed)
            parent.cancel()
        }

    @Test
    fun `exit send failure during shutdown retains transport cause`() =
        runTest {
            val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
            val scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler) + parent)
            val failure = IllegalStateException("exit send failed")
            val transport = ScriptedTransport(exitFailure = failure)
            val session = buildClient {}.start(transport, scope)
            val shutdown = async(UnconfinedTestDispatcher(testScheduler)) { runCatching { session.shutdown() } }
            runCurrent()
            assertTrue(transport.exitStarted.isCompleted)
            transport.releaseExit.complete(Unit)
            assertTrue(withTimeout(5_000) { shutdown.await() }.isFailure)
            val terminal = withTimeout(5_000) { session.awaitTermination() }
            assertTrue(generateSequence<Throwable>(terminal) { it.cause }.take(8).any { it === failure })
            assertTrue(parent.isActive)
            assertFalse(transport.closed)
            parent.cancel()
        }

    @Test
    fun `shutdown RPC failure reaches caller while termination is normal`() =
        runTest {
            val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
            val scope = CoroutineScope(backgroundScope.coroutineContext + parent)
            val transport = ScriptedTransport(shutdownError = ResponseError(-32001, "shutdown rejected"))
            val session = buildClient {}.start(transport, scope)
            val error = assertFailsWith<JsonRpcException> { withTimeout(5_000) { session.shutdown() } }
            assertEquals(-32001, error.code)
            assertEquals(null, withTimeout(5_000) { session.awaitTermination() })
            assertFalse(transport.exitStarted.isCompleted)
            assertTrue(parent.isActive)
            assertFalse(transport.closed)
            parent.cancel()
        }

    @Test
    fun `shutdown awaits exit write before closing its session`() =
        runTest {
            val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
            val sibling = Job(parent)
            val scope = CoroutineScope(backgroundScope.coroutineContext + parent)
            val transport = ScriptedTransport()
            val session = buildClient {}.start(transport, scope)
            val shutdown = async { session.shutdown() }
            runCurrent()

            assertTrue(transport.exitStarted.isCompleted)
            assertFalse(shutdown.isCompleted)
            assertFailsWith<IllegalStateException> { session.shutdown() }
            transport.releaseExit.complete(Unit)
            withTimeout(5_000) { shutdown.await() }
            assertTrue(transport.exitSent)
            assertEquals(null, withTimeout(5_000) { session.awaitTermination() })
            assertTrue(parent.isActive)
            assertTrue(sibling.isActive)
            assertFalse(transport.closed)
            parent.cancel()
        }

    @Test
    fun `peer EOF after exit frame ends shutdown normally before the exit write returns`() =
        runTest {
            val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
            val scope = CoroutineScope(backgroundScope.coroutineContext + parent)
            // The peer closes its side on reading exit while the writer's exit send is still held.
            val transport = ScriptedTransport(closeOnExit = true)
            val session = buildClient {}.start(transport, scope)
            val shutdown = async { runCatching { session.shutdown() } }
            runCurrent()

            assertTrue(transport.exitStarted.isCompleted)
            assertEquals(null, withTimeout(5_000) { shutdown.await() }.exceptionOrNull())
            assertFalse(transport.exitSent)
            assertEquals(null, withTimeout(5_000) { session.awaitTermination() })
            assertTrue(parent.isActive)
            assertFalse(transport.closed)
            parent.cancel()
        }
}

private class IdleTransport : Transport {
    val readerStopped = CompletableDeferred<Unit>()
    var closed = false
    override val isConnected: Boolean = true
    override val incoming: Flow<ByteArray> =
        flow {
            try {
                awaitCancellation()
            } finally {
                readerStopped.complete(Unit)
            }
        }

    override suspend fun send(data: ByteArray) = Unit

    override suspend fun close() {
        closed = true
    }
}

private class ScriptedTransport(
    private val failure: Throwable? = null,
    private val shutdownError: ResponseError? = null,
    private val exitFailure: Throwable? = null,
    private val closeOnExit: Boolean = false,
) : Transport {
    val messages = Channel<ByteArray>(Channel.UNLIMITED)
    val exitStarted = CompletableDeferred<Unit>()
    val releaseExit = CompletableDeferred<Unit>()
    var exitSent = false
    var closed = false
    override val isConnected: Boolean = true
    override val incoming: Flow<ByteArray> = messages.receiveAsFlow()

    override suspend fun send(data: ByteArray) {
        failure?.let { throw it }
        when (val message = JsonRpcMessageDecoder().feed(data).single()) {
            is RequestMessage ->
                if (message.method == "build/shutdown") {
                    val response =
                        shutdownError?.let { ResponseMessage.error(message.id, it) }
                            ?: ResponseMessage.success(message.id, null)
                    messages.send(JsonRpcCodec.Default.encode(response))
                }
            is NotificationMessage ->
                if (message.method == "build/exit") {
                    exitStarted.complete(Unit)
                    if (closeOnExit) messages.close()
                    releaseExit.await()
                    exitFailure?.let { throw it }
                    exitSent = true
                }
            else -> Unit
        }
    }

    override suspend fun close() {
        closed = true
    }
}
