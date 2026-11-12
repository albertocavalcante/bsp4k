package io.bsp4k.client

import io.jsonrpc4k.transport.Transport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
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
    fun `close stops the idle reader without canceling the supplied scope or transport`() =
        runTest {
            val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
            val scope = CoroutineScope(backgroundScope.coroutineContext + parent)
            val transport = IdleTransport()
            val session = buildClient {}.start(transport, scope)
            runCurrent()

            session.close()
            runCurrent()

            assertTrue(parent.isActive)
            assertTrue(transport.readerStopped.isCompleted)
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
