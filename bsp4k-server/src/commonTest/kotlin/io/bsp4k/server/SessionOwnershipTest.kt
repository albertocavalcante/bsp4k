package io.bsp4k.server

import io.jsonrpc4k.core.JsonRpcCodec
import io.jsonrpc4k.core.NotificationMessage
import io.jsonrpc4k.transport.Transport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SessionOwnershipTest {
    @Test
    fun `exit stops the reader without canceling the supplied scope or transport`() =
        runTest {
            val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
            val scope = CoroutineScope(backgroundScope.coroutineContext + parent)
            val transport = ExitTransport()
            buildServer {}.start(transport, scope)
            runCurrent()

            transport.messages.send(JsonRpcCodec.Default.encode(NotificationMessage("build/exit")))
            runCurrent()

            assertTrue(parent.isActive)
            assertTrue(transport.readerStopped.isCompleted)
            assertFalse(transport.closed)
            parent.cancel()
        }
}

private class ExitTransport : Transport {
    val messages = Channel<ByteArray>(Channel.UNLIMITED)
    val readerStopped = CompletableDeferred<Unit>()
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

    override suspend fun send(data: ByteArray) = Unit

    override suspend fun close() {
        closed = true
    }
}
