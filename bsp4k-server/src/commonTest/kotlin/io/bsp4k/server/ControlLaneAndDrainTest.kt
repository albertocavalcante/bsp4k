package io.bsp4k.server

import io.bsp4k.protocol.BspMethods
import io.bsp4k.protocol.BuildTargetIdentifier
import io.bsp4k.protocol.ReadParams
import io.bsp4k.protocol.RunParams
import io.bsp4k.protocol.RunProvider
import io.bsp4k.protocol.RunResult
import io.bsp4k.protocol.StatusCode
import io.bsp4k.protocol.WorkspaceBuildTargetsResult
import io.jsonrpc4k.core.BoundedDispatchPolicy
import io.jsonrpc4k.core.ResponseError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/** `run/readStdin` reaches a running `buildTarget/run`, and admitted requests are answered after EOF. */
@OptIn(ExperimentalCoroutinesApi::class)
class ControlLaneAndDrainTest {
    private val target = BuildTargetIdentifier("file:///workspace/app")

    /** A server whose `run` handler finishes only once it has received stdin for its origin. */
    private fun interactiveRunServer(
        started: CompletableDeferred<Unit>,
        lines: MutableList<String>,
    ): BuildServerConfig {
        val input = CompletableDeferred<String>()
        return buildServer {
            capabilities { runProvider = RunProvider(listOf("kotlin")) }
            buildTarget {
                run { params ->
                    started.complete(Unit)
                    lines += "run read ${input.await()}"
                    RunResult(originId = params.originId, statusCode = StatusCode.Ok)
                }
            }
            run {
                readStdin { params ->
                    lines += "stdin ${params.originId}"
                    input.complete(params.message)
                }
            }
        }
    }

    /** Starts the interactive server with [policy], or with the default policy when it is null. */
    private suspend fun TestScope.runWithStdin(policy: BoundedDispatchPolicy?) {
        val started = CompletableDeferred<Unit>()
        val lines = mutableListOf<String>()
        val config = interactiveRunServer(started, lines)
        val transport = MemoryTransport()
        val server = if (policy == null) config.start(transport, backgroundScope) else config.start(transport, backgroundScope, policy)
        try {
            val peer = TestClient(transport)
            peer.handshake()
            val run =
                peer.sendRequest(
                    BspMethods.BUILD_TARGET_RUN,
                    encode(RunParams.serializer(), RunParams(target = target, originId = "run-1")),
                )
            withTimeout(5_000) { started.await() }
            peer.notify(BspMethods.RUN_READ_STDIN, encode(ReadParams.serializer(), ReadParams("run-1", message = "42\n")))

            val response = peer.response(run)
            assertNull(response.error, "run failed: ${response.error}")
            assertEquals(listOf("stdin run-1", "run read 42\n"), lines)
        } finally {
            server.close()
        }
    }

    @Test
    fun `a running run handler receives stdin with the default policy`() = runTest { runWithStdin(policy = null) }

    @Test
    fun `a running run handler receives stdin with a caller policy that lists no control notifications`() =
        runTest { runWithStdin(policy = BoundedDispatchPolicy(maxConcurrentRequests = 4, maxQueuedMessages = 64)) }

    @Test
    fun `stdin is accepted from the initialize result on while barrier notifications are still dropped`() =
        runTest {
            val received = mutableListOf<String>()
            val (server, peer) =
                buildServer {
                    run { readStdin { params -> received += "stdin ${params.originId}" } }
                    onNotification("custom/didChange") { received += "didChange" }
                }.connect(backgroundScope)
            val stdin = encode(ReadParams.serializer(), ReadParams("run-0", message = "early\n"))

            peer.notify(BspMethods.RUN_READ_STDIN, stdin)
            delay(100)
            assertEquals(emptyList<String>(), received, "stdin before build/initialize must be dropped")

            assertNull(peer.initialize().error)
            assertEquals(BuildServerState.AwaitingInitialized, server.state.value)
            peer.notify(BspMethods.RUN_READ_STDIN, encode(ReadParams.serializer(), ReadParams("run-1", message = "x\n")))
            peer.notify("custom/didChange")
            delay(100)
            assertEquals(listOf("stdin run-1"), received)
            server.close()
        }

    @Test
    fun `other notifications stay ordering barriers behind running requests`() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val order = mutableListOf<String>()
            val (server, peer) =
                buildServer {
                    workspace {
                        buildTargets {
                            entered.complete(Unit)
                            release.await()
                            order += "targets"
                            WorkspaceBuildTargetsResult(emptyList())
                        }
                    }
                    onNotification("custom/didChange") { order += "didChange" }
                }.connect(backgroundScope)
            peer.handshake()

            val targets = peer.sendRequest(BspMethods.WORKSPACE_BUILD_TARGETS)
            withTimeout(5_000) { entered.await() }
            peer.notify("custom/didChange")
            delay(100)
            assertFalse("didChange" in order, "a barrier notification ran before an earlier request finished")
            release.complete(Unit)

            assertNull(peer.response(targets).error)
            assertNull(peer.request(BspMethods.WORKSPACE_BUILD_TARGETS).error)
            assertEquals(listOf("targets", "didChange", "targets"), order)
            server.close()
        }

    @Test
    fun `a request admitted before the client closes its output is still answered`() =
        runTest {
            val (server, peer, transport) = connectWithTransport(slowTargetsServer(), BoundedDispatchPolicy(16, 256))
            peer.handshake()

            val targets = peer.sendRequest(BspMethods.WORKSPACE_BUILD_TARGETS)
            transport.toServer.close()

            val response = peer.response(targets)
            assertNull(response.error, "unexpected error: ${response.error}")
            assertNull(withTimeout(5_000) { server.awaitTermination() })
        }

    @Test
    fun `the drain timeout from the policy bounds the wait for admitted requests after EOF`() =
        runTest {
            val config =
                buildServer {
                    workspace { buildTargets { awaitCancellation() } }
                }
            val policy = BoundedDispatchPolicy(16, 256, drainTimeout = 200.milliseconds)
            val (server, peer, transport) = connectWithTransport(config, policy)
            peer.handshake()

            val targets = peer.sendRequest(BspMethods.WORKSPACE_BUILD_TARGETS)
            val closedAt = testScheduler.currentTime
            transport.toServer.close()

            assertError(ResponseError.REQUEST_CANCELLED, peer.response(targets))
            assertNull(withTimeout(5_000) { server.awaitTermination() })
            val elapsed = testScheduler.currentTime - closedAt
            assertTrue(elapsed < 1_000, "teardown took $elapsed ms, so the 200 ms policy drain timeout was not used")
        }

    private fun slowTargetsServer(): BuildServerConfig =
        buildServer {
            workspace {
                buildTargets {
                    delay(500)
                    WorkspaceBuildTargetsResult(emptyList())
                }
            }
        }

    private data class Session(
        val server: BuildServer,
        val peer: TestClient,
        val transport: MemoryTransport,
    )

    private suspend fun TestScope.connectWithTransport(
        config: BuildServerConfig,
        policy: BoundedDispatchPolicy,
    ): Session {
        val transport = MemoryTransport()
        return Session(config.start(transport, backgroundScope, policy), TestClient(transport), transport)
    }
}
