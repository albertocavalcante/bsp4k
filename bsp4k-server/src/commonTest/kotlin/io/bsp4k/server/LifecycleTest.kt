package io.bsp4k.server

import io.bsp4k.protocol.BSP_VERSION
import io.bsp4k.protocol.BspDataKind
import io.bsp4k.protocol.BspMethods
import io.bsp4k.protocol.InitializeBuildParams
import io.bsp4k.protocol.InitializeBuildResult
import io.bsp4k.protocol.JvmBuildTarget
import io.bsp4k.protocol.LogMessageParams
import io.bsp4k.protocol.MessageType
import io.bsp4k.protocol.ReadParams
import io.bsp4k.protocol.WorkspaceBuildTargetsResult
import io.bsp4k.protocol.decodeData
import io.jsonrpc4k.core.JsonRpcException
import io.jsonrpc4k.core.ResponseError
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private const val CUSTOM_NOTE = "custom/note"

class LifecycleTest {
    private val stdin = ReadParams(originId = "run-1", message = "input")
    private val received = mutableListOf<String>()

    private fun recordingServer(block: BuildServerBuilder.() -> Unit = {}): BuildServerConfig =
        buildServer {
            workspace { buildTargets { WorkspaceBuildTargetsResult(emptyList()) } }
            run { readStdin { params -> received += "stdin:${params.message}" } }
            onNotification(CUSTOM_NOTE) { received += "note" }
            block()
        }

    private suspend fun TestClient.buildTargets() = request(BspMethods.WORKSPACE_BUILD_TARGETS)

    @Test
    fun `requests before initialize fail with ServerNotInitialized and notifications are dropped`() =
        runTest {
            val (server, peer) =
                recordingServer { lifecycle { onInitialized { received += "initialized" } } }.connect(backgroundScope)

            assertError(ResponseError.SERVER_NOT_INITIALIZED, peer.buildTargets())
            assertError(ResponseError.SERVER_NOT_INITIALIZED, peer.request(BspMethods.BUILD_SHUTDOWN))
            peer.notify(BspMethods.RUN_READ_STDIN, encode(ReadParams.serializer(), stdin))
            peer.notify(BspMethods.BUILD_INITIALIZED)
            assertError(ResponseError.SERVER_NOT_INITIALIZED, peer.buildTargets())

            assertEquals(emptyList(), received)
            assertEquals(BuildServerState.Uninitialized, server.state.value)
        }

    @Test
    fun `initialize hook reads client params and sets result data`() =
        runTest {
            val kind = BspDataKind<InitializeBuildResult, JvmBuildTarget>("example-jvm", JvmBuildTarget.serializer())
            var seen: InitializeBuildParams? = null
            val (server, peer) =
                buildServer {
                    serverInfo("hooked", "2.0")
                    lifecycle {
                        onInitialize { params ->
                            seen = params
                            data(kind, JvmBuildTarget(javaVersion = "17"))
                        }
                    }
                }.connect(backgroundScope)

            val response = peer.initialize()

            val result = testJson.decodeFromJsonElement(InitializeBuildResult.serializer(), assertNotNull(response.result))
            assertEquals(clientInitializeParams, seen)
            assertEquals("hooked", result.displayName)
            assertEquals("2.0", result.version)
            assertEquals(BSP_VERSION, result.bspVersion)
            assertEquals("example-jvm", result.dataKind)
            assertEquals(JvmBuildTarget(javaVersion = "17"), result.decodeData(kind))
            assertEquals(BuildServerState.AwaitingInitialized, server.state.value)
        }

    @Test
    fun `initialized opens application handlers and runs its hook once`() =
        runTest {
            val (server, peer) =
                recordingServer {
                    lifecycle {
                        onInitialized {
                            received += "initialized"
                            client.logMessage(MessageType.Info, "ready")
                        }
                    }
                }.connect(backgroundScope)

            assertNull(peer.initialize().error)
            assertError(ResponseError.SERVER_NOT_INITIALIZED, peer.buildTargets())
            // A barrier notification is still dropped here; run/readStdin, a control notification, is
            // accepted from AwaitingInitialized on (see ControlLaneAndDrainTest).
            peer.notify(CUSTOM_NOTE)
            peer.notify(BspMethods.BUILD_INITIALIZED)
            assertNull(peer.buildTargets().error)
            peer.notify(BspMethods.BUILD_INITIALIZED)
            peer.notify(BspMethods.RUN_READ_STDIN, encode(ReadParams.serializer(), stdin))
            assertNull(peer.buildTargets().error)

            assertEquals(listOf("initialized", "stdin:input"), received)
            assertEquals(BuildServerState.Running, server.state.value)
            val ready = peer.notification()
            assertEquals(BspMethods.BUILD_LOG_MESSAGE, ready.method)
            val log = testJson.decodeFromJsonElement(LogMessageParams.serializer(), assertNotNull(ready.params))
            assertEquals("ready", log.message)
        }

    @Test
    fun `a second initialize fails with InvalidRequest`() =
        runTest {
            val (server, peer) = recordingServer().connect(backgroundScope)

            assertNull(peer.initialize().error)
            assertError(ResponseError.INVALID_REQUEST, peer.initialize())
            peer.notify(BspMethods.BUILD_INITIALIZED)
            assertError(ResponseError.INVALID_REQUEST, peer.initialize())

            assertNull(peer.buildTargets().error)
            assertEquals(BuildServerState.Running, server.state.value)
        }

    @Test
    fun `undecodable initialize params fail with InvalidParams and allow a retry`() =
        runTest {
            val (server, peer) = recordingServer().connect(backgroundScope)

            assertError(ResponseError.INVALID_PARAMS, peer.initialize(params = null))
            assertError(ResponseError.INVALID_PARAMS, peer.initialize(JsonObject(mapOf("displayName" to JsonPrimitive("x")))))
            assertError(ResponseError.INVALID_PARAMS, peer.initialize(JsonArray(listOf(JsonPrimitive(42)))))
            assertEquals(BuildServerState.Uninitialized, server.state.value)

            peer.handshake()
            assertNull(peer.buildTargets().error)
        }

    @Test
    fun `a rejecting initialize hook keeps the server uninitialized`() =
        runTest {
            var attempts = 0
            val (server, peer) =
                recordingServer {
                    lifecycle {
                        onInitialize {
                            attempts += 1
                            if (attempts == 1) throw JsonRpcException(ResponseError.REQUEST_FAILED, "not yet")
                        }
                    }
                }.connect(backgroundScope)

            assertError(ResponseError.REQUEST_FAILED, peer.initialize())
            assertEquals(BuildServerState.Uninitialized, server.state.value)
            assertError(ResponseError.SERVER_NOT_INITIALIZED, peer.buildTargets())

            peer.handshake()
            assertNull(peer.buildTargets().error)
            assertEquals(2, attempts)
        }

    @Test
    fun `shutdown runs its hook then refuses requests and drops notifications`() =
        runTest {
            val (server, peer) =
                recordingServer { lifecycle { onShutdown { received += "shutdown" } } }.connect(backgroundScope)
            peer.handshake()

            assertNullResult(peer.request(BspMethods.BUILD_SHUTDOWN))
            assertEquals(BuildServerState.ShutdownRequested, server.state.value)
            assertError(ResponseError.INVALID_REQUEST, peer.buildTargets())
            assertError(ResponseError.INVALID_REQUEST, peer.request(BspMethods.BUILD_SHUTDOWN))
            assertError(ResponseError.INVALID_REQUEST, peer.initialize())
            peer.notify(BspMethods.RUN_READ_STDIN, encode(ReadParams.serializer(), stdin))
            assertError(ResponseError.INVALID_REQUEST, peer.buildTargets())

            assertEquals(listOf("shutdown"), received)
        }

    @Test
    fun `shutdown is accepted before initialized`() =
        runTest {
            val (server, peer) = recordingServer().connect(backgroundScope)

            assertNull(peer.initialize().error)
            assertNullResult(peer.request(BspMethods.BUILD_SHUTDOWN))

            assertEquals(BuildServerState.ShutdownRequested, server.state.value)
        }

    @Test
    fun `exit after shutdown reports exit code 0 and ends the session`() =
        runTest {
            val exitCodes = mutableListOf<Int>()
            val (server, peer) =
                recordingServer { lifecycle { onExit { exitCode -> exitCodes += exitCode } } }.connect(backgroundScope)
            peer.handshake()
            assertNullResult(peer.request(BspMethods.BUILD_SHUTDOWN))

            peer.notify(BspMethods.BUILD_EXIT)

            assertNull(withTimeout(5_000) { server.awaitTermination() })
            assertEquals(listOf(0), exitCodes)
            assertEquals(BuildServerState.Exited, server.state.value)
        }

    @Test
    fun `messages sent by the exit hook are delivered`() =
        runTest {
            val (server, peer) =
                recordingServer {
                    lifecycle { onExit { client.logMessage(MessageType.Info, "bye") } }
                }.connect(backgroundScope)
            peer.handshake()
            assertNullResult(peer.request(BspMethods.BUILD_SHUTDOWN))

            peer.notify(BspMethods.BUILD_EXIT)

            assertNull(withTimeout(5_000) { server.awaitTermination() })
            val bye = peer.notification()
            assertEquals(BspMethods.BUILD_LOG_MESSAGE, bye.method)
        }

    @Test
    fun `exit without shutdown reports exit code 1`() =
        runTest {
            val exitCodes = mutableListOf<Int>()
            val (server, peer) =
                recordingServer { lifecycle { onExit { exitCode -> exitCodes += exitCode } } }.connect(backgroundScope)
            peer.handshake()
            assertNull(peer.buildTargets().error)

            peer.notify(BspMethods.BUILD_EXIT)

            assertNull(withTimeout(5_000) { server.awaitTermination() })
            assertEquals(listOf(1), exitCodes)
        }

    @Test
    fun `exit before initialize ends the session with exit code 1`() =
        runTest {
            val exitCodes = mutableListOf<Int>()
            val (server, peer) =
                recordingServer { lifecycle { onExit { exitCode -> exitCodes += exitCode } } }.connect(backgroundScope)

            peer.notify(BspMethods.BUILD_EXIT)

            assertNull(withTimeout(5_000) { server.awaitTermination() })
            assertEquals(listOf(1), exitCodes)
            assertEquals(BuildServerState.Exited, server.state.value)
        }

    @Test
    fun `each lifecycle hook registers once`() {
        assertFailsWith<IllegalArgumentException> { buildServer { lifecycle { repeat(2) { onInitialize {} } } } }
        assertFailsWith<IllegalArgumentException> { buildServer { lifecycle { repeat(2) { onInitialized {} } } } }
        assertFailsWith<IllegalArgumentException> { buildServer { lifecycle { repeat(2) { onShutdown {} } } } }
        assertFailsWith<IllegalArgumentException> { buildServer { lifecycle { repeat(2) { onExit {} } } } }
    }
}
