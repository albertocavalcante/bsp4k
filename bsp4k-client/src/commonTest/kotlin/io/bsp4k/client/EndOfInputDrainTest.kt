package io.bsp4k.client

import io.bsp4k.protocol.BspMethods
import io.jsonrpc4k.core.BoundedDispatchPolicy
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

/** Notifications a server sends right before it closes its output still reach their handlers. */
class EndOfInputDrainTest {
    @Test
    fun `a notification admitted before the server closes its output finishes its handler`() =
        runTest {
            val logged = mutableListOf<String>()
            val peer = FakePeer()
            val session =
                connect(
                    peer,
                    buildClient {
                        onLogMessage { params ->
                            delay(500)
                            logged += params.message
                        }
                    },
                )

            peer.push(BspMethods.BUILD_LOG_MESSAGE, """{"type":4,"message":"last words"}""")
            peer.close()

            assertNull(withTimeout(5_000) { session.awaitTermination() })
            assertEquals(listOf("last words"), logged)
        }

    @Test
    fun `the drain timeout from the policy bounds the wait after EOF`() =
        runTest {
            val peer = FakePeer()
            val config = buildClient { onLogMessage { awaitCancellation() } }
            val session = config.start(peer, backgroundScope, BoundedDispatchPolicy(16, 256, drainTimeout = 200.milliseconds))
            session.initialize()

            peer.push(BspMethods.BUILD_LOG_MESSAGE, """{"type":4,"message":"stuck"}""")
            peer.close()

            assertNull(withTimeout(5_000) { session.awaitTermination() })
        }
}
