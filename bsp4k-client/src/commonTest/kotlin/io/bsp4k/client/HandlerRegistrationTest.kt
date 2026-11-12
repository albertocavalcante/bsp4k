package io.bsp4k.client

import io.bsp4k.protocol.BspMethods
import io.jsonrpc4k.core.NotificationHandler
import io.jsonrpc4k.transport.Transport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class HandlerRegistrationTest {
    @Test
    fun `reserved and duplicate notifications fail`() {
        assertFailsWith<IllegalArgumentException> { buildClient { onNotification("$/cancelRequest") {} } }
        assertFailsWith<IllegalArgumentException> {
            buildClient {
                onLogMessage {}
                onLogMessage {}
            }
        }
        assertFailsWith<IllegalArgumentException> {
            buildClient {
                onLogMessage {}
                onNotification(BspMethods.BUILD_LOG_MESSAGE) {}
            }
        }
        assertFailsWith<IllegalArgumentException> {
            buildClient {
                onNotification(BspMethods.BUILD_LOG_MESSAGE) {}
                onLogMessage {}
            }
        }
        assertFailsWith<IllegalArgumentException> {
            buildClient {
                onNotification("custom") {}
                onNotification("custom") {}
            }
        }
    }

    @Test
    fun `start rejects reserved notification from copied config before using transport`() =
        runTest {
            val config = buildClient {}.copy(notificationHandlers = mapOf("$/cancelRequest" to NotificationHandler {}))
            assertFailsWith<IllegalArgumentException> { config.start(UnusedTransport, backgroundScope) }
        }
}

private object UnusedTransport : Transport {
    override val incoming: Flow<ByteArray> get() = error("transport must not be used")
    override val isConnected: Boolean get() = error("transport must not be used")

    override suspend fun send(data: ByteArray): Unit = error("transport must not be used")

    override suspend fun close(): Unit = error("transport must not be used")
}
