package io.bsp4k.server

import io.bsp4k.protocol.BspMethods
import io.bsp4k.protocol.BuildTargetIdentifier
import io.bsp4k.protocol.CompileParams
import io.bsp4k.protocol.CompileProvider
import io.bsp4k.protocol.WorkspaceBuildTargetsResult
import io.jsonrpc4k.core.RequestId
import io.jsonrpc4k.core.ResponseError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The default `start` runs handlers concurrently and honors `$/cancelRequest`. */
class ConcurrentDispatchTest {
    private val target = BuildTargetIdentifier("file:///workspace/app")

    @Test
    fun `a cancelled compile is answered while a concurrent request completes`() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            val observed = CompletableDeferred<Unit>()
            val (server, peer) =
                buildServer {
                    capabilities { compileProvider = CompileProvider(listOf("kotlin")) }
                    workspace { buildTargets { WorkspaceBuildTargetsResult(emptyList()) } }
                    buildTarget {
                        compile {
                            entered.complete(Unit)
                            try {
                                awaitCancellation()
                            } finally {
                                observed.complete(Unit)
                            }
                        }
                    }
                }.connect(backgroundScope)
            peer.handshake()

            val compile =
                peer.sendRequest(
                    BspMethods.BUILD_TARGET_COMPILE,
                    encode(CompileParams.serializer(), CompileParams(listOf(target))),
                )
            withTimeout(5_000) { entered.await() }

            val targets = peer.request(BspMethods.WORKSPACE_BUILD_TARGETS)
            assertEquals(null, targets.error)

            val number = assertIs<RequestId.NumberId>(compile).value
            peer.notify(BspMethods.CANCEL_REQUEST, JsonObject(mapOf("id" to JsonPrimitive(number))))

            assertError(ResponseError.REQUEST_CANCELLED, peer.response(compile))
            withTimeout(5_000) { observed.await() }
            server.close()
        }

    @Test
    fun `build initialized runs before later requests`() =
        runTest {
            val order = mutableListOf<String>()
            val (server, peer) =
                buildServer {
                    lifecycle { onInitialized { order.add("initialized") } }
                    workspace {
                        buildTargets {
                            order.add("targets")
                            WorkspaceBuildTargetsResult(emptyList())
                        }
                    }
                }.connect(backgroundScope)
            peer.handshake()
            assertEquals(null, peer.request(BspMethods.WORKSPACE_BUILD_TARGETS).error)
            assertEquals(listOf("initialized", "targets"), order)
            assertTrue(server.state.value == BuildServerState.Running)
            server.close()
        }
}
