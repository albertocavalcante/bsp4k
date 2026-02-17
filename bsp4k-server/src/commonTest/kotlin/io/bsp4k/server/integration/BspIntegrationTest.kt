package io.bsp4k.server.integration

import io.bsp4k.protocol.BuildClientCapabilities
import io.bsp4k.protocol.BuildServerCapabilities
import io.bsp4k.protocol.BuildTarget
import io.bsp4k.protocol.BuildTargetCapabilities
import io.bsp4k.protocol.BuildTargetIdentifier
import io.bsp4k.protocol.CompileParams
import io.bsp4k.protocol.CompileProvider
import io.bsp4k.protocol.CompileResult
import io.bsp4k.protocol.Diagnostic
import io.bsp4k.protocol.DiagnosticSeverity
import io.bsp4k.protocol.InitializeBuildParams
import io.bsp4k.protocol.InitializeBuildResult
import io.bsp4k.protocol.LogMessageParams
import io.bsp4k.protocol.MessageType
import io.bsp4k.protocol.Position
import io.bsp4k.protocol.PublishDiagnosticsParams
import io.bsp4k.protocol.Range
import io.bsp4k.protocol.SourceItem
import io.bsp4k.protocol.SourceItemKind
import io.bsp4k.protocol.SourcesItem
import io.bsp4k.protocol.SourcesParams
import io.bsp4k.protocol.SourcesResult
import io.bsp4k.protocol.StatusCode
import io.bsp4k.protocol.TaskFinishParams
import io.bsp4k.protocol.TaskId
import io.bsp4k.protocol.TaskStartParams
import io.bsp4k.protocol.TextDocumentIdentifier
import io.bsp4k.protocol.WorkspaceBuildTargetsResult
import io.jsonrpc4k.core.Connection
import io.jsonrpc4k.core.JsonRpcCodec
import io.jsonrpc4k.core.JsonRpcException
import io.jsonrpc4k.core.ResponseError
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * BSP method constants for testing.
 */
private object Methods {
    const val BUILD_INITIALIZE = "build/initialize"
    const val BUILD_INITIALIZED = "build/initialized"
    const val BUILD_SHUTDOWN = "build/shutdown"
    const val BUILD_EXIT = "build/exit"
    const val WORKSPACE_BUILD_TARGETS = "workspace/buildTargets"
    const val BUILD_TARGET_SOURCES = "buildTarget/sources"
    const val BUILD_TARGET_COMPILE = "buildTarget/compile"
    const val BUILD_LOG_MESSAGE = "build/logMessage"
    const val BUILD_TASK_START = "build/taskStart"
    const val BUILD_TASK_FINISH = "build/taskFinish"
    const val BUILD_PUBLISH_DIAGNOSTICS = "build/publishDiagnostics"
}

/**
 * Integration tests for the BSP server implementation.
 *
 * These tests wire two Connection instances (client <-> server) directly,
 * verifying the full BSP flow without depending on transport.
 */
class BspIntegrationTest {
    private val json = JsonRpcCodec.defaultJson

    // ===== Test 1: Initialize Handshake =====

    @Test
    fun `initialize handshake completes successfully`() =
        runTest {
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val clientConnection = Connection(scope = scope)
            val serverConnection = Connection(scope = scope)

            val capabilities =
                BuildServerCapabilities(
                    compileProvider = CompileProvider(languageIds = listOf("kotlin")),
                )

            serverConnection.onRequest(Methods.BUILD_INITIALIZE) { params: JsonElement? ->
                val initParams =
                    params?.let {
                        json.decodeFromJsonElement(InitializeBuildParams.serializer(), it)
                    }
                initParams shouldNotBe null
                initParams!!.displayName shouldBe "test-client"

                val result =
                    InitializeBuildResult(
                        displayName = "TestBuildServer",
                        version = "1.0.0",
                        bspVersion = "2.1.0",
                        capabilities = capabilities,
                    )
                json.encodeToJsonElement(InitializeBuildResult.serializer(), result)
            }

            var initializedReceived = false
            serverConnection.onNotification(Methods.BUILD_INITIALIZED) { _: JsonElement? ->
                initializedReceived = true
            }

            launch {
                clientConnection.outgoing.collect { bytes ->
                    serverConnection.receive(bytes)
                }
            }
            launch {
                serverConnection.outgoing.collect { bytes ->
                    clientConnection.receive(bytes)
                }
            }

            val initParams =
                InitializeBuildParams(
                    displayName = "test-client",
                    version = "1.0.0",
                    bspVersion = "2.1.0",
                    rootUri = "file:///test",
                    capabilities = BuildClientCapabilities(languageIds = listOf("kotlin")),
                )
            val initResult =
                clientConnection.request<InitializeBuildParams, InitializeBuildResult>(
                    Methods.BUILD_INITIALIZE,
                    initParams,
                )

            initResult shouldNotBe null
            initResult!!.displayName shouldBe "TestBuildServer"
            initResult.version shouldBe "1.0.0"
            initResult.bspVersion shouldBe "2.1.0"
            initResult.capabilities.compileProvider shouldNotBe null

            clientConnection.notify(Methods.BUILD_INITIALIZED, null as JsonElement?)
            delay(100)
            initializedReceived shouldBe true

            clientConnection.close()
            serverConnection.close()
            scope.cancel()
        }

    // ===== Test 2: workspace/buildTargets =====

    @Test
    fun `workspace buildTargets returns target list`() =
        runTest {
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val clientConnection = Connection(scope = scope)
            val serverConnection = Connection(scope = scope)

            val targets =
                WorkspaceBuildTargetsResult(
                    targets =
                        listOf(
                            BuildTarget(
                                id = BuildTargetIdentifier(uri = "file:///project/main"),
                                displayName = "main",
                                capabilities = BuildTargetCapabilities(canCompile = true),
                                languageIds = listOf("kotlin"),
                            ),
                            BuildTarget(
                                id = BuildTargetIdentifier(uri = "file:///project/test"),
                                displayName = "test",
                                capabilities = BuildTargetCapabilities(canTest = true),
                                languageIds = listOf("kotlin"),
                            ),
                        ),
                )

            serverConnection.onRequest(Methods.WORKSPACE_BUILD_TARGETS) { _: JsonElement? ->
                json.encodeToJsonElement(WorkspaceBuildTargetsResult.serializer(), targets)
            }

            launch {
                clientConnection.outgoing.collect { bytes ->
                    serverConnection.receive(bytes)
                }
            }
            launch {
                serverConnection.outgoing.collect { bytes ->
                    clientConnection.receive(bytes)
                }
            }

            val result =
                clientConnection.request<JsonElement?, WorkspaceBuildTargetsResult>(
                    Methods.WORKSPACE_BUILD_TARGETS,
                    null,
                )

            result shouldNotBe null
            result!!.targets shouldHaveSize 2
            result.targets[0].displayName shouldBe "main"
            result.targets[0].capabilities.canCompile shouldBe true
            result.targets[1].displayName shouldBe "test"
            result.targets[1].capabilities.canTest shouldBe true

            clientConnection.close()
            serverConnection.close()
            scope.cancel()
        }

    // ===== Test 3: buildTarget/sources =====

    @Test
    fun `buildTarget sources returns source items`() =
        runTest {
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val clientConnection = Connection(scope = scope)
            val serverConnection = Connection(scope = scope)

            serverConnection.onRequest(Methods.BUILD_TARGET_SOURCES) { params: JsonElement? ->
                val sourcesParams =
                    params?.let {
                        json.decodeFromJsonElement(SourcesParams.serializer(), it)
                    }
                sourcesParams shouldNotBe null

                val result =
                    SourcesResult(
                        items =
                            sourcesParams!!.targets.map { target ->
                                SourcesItem(
                                    target = target,
                                    sources =
                                        listOf(
                                            SourceItem(
                                                uri = "file:///src/Main.kt",
                                                kind = SourceItemKind.File,
                                            ),
                                        ),
                                    roots = listOf("file:///src"),
                                )
                            },
                    )
                json.encodeToJsonElement(SourcesResult.serializer(), result)
            }

            launch {
                clientConnection.outgoing.collect { bytes ->
                    serverConnection.receive(bytes)
                }
            }
            launch {
                serverConnection.outgoing.collect { bytes ->
                    clientConnection.receive(bytes)
                }
            }

            val result =
                clientConnection.request<SourcesParams, SourcesResult>(
                    Methods.BUILD_TARGET_SOURCES,
                    SourcesParams(
                        targets = listOf(BuildTargetIdentifier(uri = "file:///project/main")),
                    ),
                )

            result shouldNotBe null
            result!!.items shouldHaveSize 1
            result.items[0].sources shouldHaveSize 1
            result.items[0].sources[0].uri shouldBe "file:///src/Main.kt"
            result.items[0].sources[0].kind shouldBe SourceItemKind.File

            clientConnection.close()
            serverConnection.close()
            scope.cancel()
        }

    // ===== Test 4: buildTarget/compile =====

    @Test
    fun `buildTarget compile returns status code`() =
        runTest {
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val clientConnection = Connection(scope = scope)
            val serverConnection = Connection(scope = scope)

            serverConnection.onRequest(Methods.BUILD_TARGET_COMPILE) { params: JsonElement? ->
                val compileParams =
                    params?.let {
                        json.decodeFromJsonElement(CompileParams.serializer(), it)
                    }
                compileParams shouldNotBe null

                val result =
                    CompileResult(
                        originId = compileParams!!.originId,
                        statusCode = StatusCode.Ok,
                    )
                json.encodeToJsonElement(CompileResult.serializer(), result)
            }

            launch {
                clientConnection.outgoing.collect { bytes ->
                    serverConnection.receive(bytes)
                }
            }
            launch {
                serverConnection.outgoing.collect { bytes ->
                    clientConnection.receive(bytes)
                }
            }

            val result =
                clientConnection.request<CompileParams, CompileResult>(
                    Methods.BUILD_TARGET_COMPILE,
                    CompileParams(
                        targets = listOf(BuildTargetIdentifier(uri = "file:///project/main")),
                        originId = "compile-1",
                    ),
                )

            result shouldNotBe null
            result!!.statusCode shouldBe StatusCode.Ok
            result.originId shouldBe "compile-1"

            clientConnection.close()
            serverConnection.close()
            scope.cancel()
        }

    // ===== Test 5: Server -> Client Notifications =====

    @Test
    fun `server sends task progress notifications to client`() =
        runTest {
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val clientConnection = Connection(scope = scope)
            val serverConnection = Connection(scope = scope)

            var receivedTaskStart: TaskStartParams? = null
            var receivedTaskFinish: TaskFinishParams? = null

            clientConnection.onNotification(Methods.BUILD_TASK_START) { params: JsonElement? ->
                receivedTaskStart =
                    params?.let {
                        json.decodeFromJsonElement(TaskStartParams.serializer(), it)
                    }
            }

            clientConnection.onNotification(Methods.BUILD_TASK_FINISH) { params: JsonElement? ->
                receivedTaskFinish =
                    params?.let {
                        json.decodeFromJsonElement(TaskFinishParams.serializer(), it)
                    }
            }

            launch {
                clientConnection.outgoing.collect { bytes ->
                    serverConnection.receive(bytes)
                }
            }
            launch {
                serverConnection.outgoing.collect { bytes ->
                    clientConnection.receive(bytes)
                }
            }

            // Server sends task start
            val taskStart =
                TaskStartParams(
                    taskId = TaskId(id = "compile-1"),
                    message = "Compiling main",
                )
            serverConnection.notify(
                Methods.BUILD_TASK_START,
                json.encodeToJsonElement(TaskStartParams.serializer(), taskStart),
            )

            // Server sends task finish
            val taskFinish =
                TaskFinishParams(
                    taskId = TaskId(id = "compile-1"),
                    status = StatusCode.Ok,
                    message = "Compilation succeeded",
                )
            serverConnection.notify(
                Methods.BUILD_TASK_FINISH,
                json.encodeToJsonElement(TaskFinishParams.serializer(), taskFinish),
            )

            delay(200)

            receivedTaskStart shouldNotBe null
            receivedTaskStart!!.taskId.id shouldBe "compile-1"
            receivedTaskStart!!.message shouldBe "Compiling main"

            receivedTaskFinish shouldNotBe null
            receivedTaskFinish!!.taskId.id shouldBe "compile-1"
            receivedTaskFinish!!.status shouldBe StatusCode.Ok

            clientConnection.close()
            serverConnection.close()
            scope.cancel()
        }

    @Test
    fun `server sends diagnostics notification to client`() =
        runTest {
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val clientConnection = Connection(scope = scope)
            val serverConnection = Connection(scope = scope)

            var receivedDiagnostics: PublishDiagnosticsParams? = null

            clientConnection.onNotification(Methods.BUILD_PUBLISH_DIAGNOSTICS) { params: JsonElement? ->
                receivedDiagnostics =
                    params?.let {
                        json.decodeFromJsonElement(PublishDiagnosticsParams.serializer(), it)
                    }
            }

            launch {
                clientConnection.outgoing.collect { bytes ->
                    serverConnection.receive(bytes)
                }
            }
            launch {
                serverConnection.outgoing.collect { bytes ->
                    clientConnection.receive(bytes)
                }
            }

            val diagnostics =
                PublishDiagnosticsParams(
                    textDocument = TextDocumentIdentifier(uri = "file:///src/Main.kt"),
                    buildTarget = BuildTargetIdentifier(uri = "file:///project/main"),
                    diagnostics =
                        listOf(
                            Diagnostic(
                                range = Range(Position(5, 0), Position(5, 10)),
                                message = "Unresolved reference: foo",
                                severity = DiagnosticSeverity.Error,
                            ),
                        ),
                )

            serverConnection.notify(
                Methods.BUILD_PUBLISH_DIAGNOSTICS,
                json.encodeToJsonElement(PublishDiagnosticsParams.serializer(), diagnostics),
            )

            delay(200)

            receivedDiagnostics shouldNotBe null
            receivedDiagnostics!!.textDocument.uri shouldBe "file:///src/Main.kt"
            receivedDiagnostics!!.diagnostics shouldHaveSize 1
            receivedDiagnostics!!.diagnostics[0].message shouldBe "Unresolved reference: foo"
            receivedDiagnostics!!.diagnostics[0].severity shouldBe DiagnosticSeverity.Error

            clientConnection.close()
            serverConnection.close()
            scope.cancel()
        }

    @Test
    fun `server sends logMessage notification to client`() =
        runTest {
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val clientConnection = Connection(scope = scope)
            val serverConnection = Connection(scope = scope)

            var receivedLog: LogMessageParams? = null

            clientConnection.onNotification(Methods.BUILD_LOG_MESSAGE) { params: JsonElement? ->
                receivedLog =
                    params?.let {
                        json.decodeFromJsonElement(LogMessageParams.serializer(), it)
                    }
            }

            launch {
                clientConnection.outgoing.collect { bytes ->
                    serverConnection.receive(bytes)
                }
            }
            launch {
                serverConnection.outgoing.collect { bytes ->
                    clientConnection.receive(bytes)
                }
            }

            val logParams =
                LogMessageParams(
                    type = MessageType.Info,
                    message = "Build started",
                )
            serverConnection.notify(
                Methods.BUILD_LOG_MESSAGE,
                json.encodeToJsonElement(LogMessageParams.serializer(), logParams),
            )

            delay(200)

            receivedLog shouldNotBe null
            receivedLog!!.type shouldBe MessageType.Info
            receivedLog!!.message shouldBe "Build started"

            clientConnection.close()
            serverConnection.close()
            scope.cancel()
        }

    // ===== Test 6: Error Handling =====

    @Test
    fun `request to unregistered method returns method not found error`() =
        runTest {
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val clientConnection = Connection(scope = scope)
            val serverConnection = Connection(scope = scope)

            launch {
                clientConnection.outgoing.collect { bytes ->
                    serverConnection.receive(bytes)
                }
            }
            launch {
                serverConnection.outgoing.collect { bytes ->
                    clientConnection.receive(bytes)
                }
            }

            val exception =
                assertFailsWith<JsonRpcException> {
                    clientConnection.request<SourcesParams, SourcesResult>(
                        "buildTarget/unknownMethod",
                        SourcesParams(
                            targets = listOf(BuildTargetIdentifier(uri = "file:///test")),
                        ),
                    )
                }

            exception.code shouldBe ResponseError.METHOD_NOT_FOUND

            clientConnection.close()
            serverConnection.close()
            scope.cancel()
        }

    @Test
    fun `handler exception returns proper error response`() =
        runTest {
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val clientConnection = Connection(scope = scope)
            val serverConnection = Connection(scope = scope)

            serverConnection.onRequest("test/fail") { _: JsonElement? ->
                throw JsonRpcException(
                    ResponseError.INVALID_PARAMS,
                    "Invalid build target",
                )
            }

            launch {
                clientConnection.outgoing.collect { bytes ->
                    serverConnection.receive(bytes)
                }
            }
            launch {
                serverConnection.outgoing.collect { bytes ->
                    clientConnection.receive(bytes)
                }
            }

            val exception =
                assertFailsWith<JsonRpcException> {
                    clientConnection.request<JsonElement?, String>("test/fail", null)
                }

            exception.code shouldBe ResponseError.INVALID_PARAMS
            exception.message shouldContain "Invalid build target"

            clientConnection.close()
            serverConnection.close()
            scope.cancel()
        }

    // ===== Test 7: Shutdown Flow =====

    @Test
    fun `shutdown and exit sequence completes cleanly`() =
        runTest {
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val clientConnection = Connection(scope = scope)
            val serverConnection = Connection(scope = scope)

            var shutdownReceived = false
            var exitReceived = false

            serverConnection.onRequest(Methods.BUILD_SHUTDOWN) { _: JsonElement? ->
                shutdownReceived = true
                null
            }

            serverConnection.onNotification(Methods.BUILD_EXIT) { _: JsonElement? ->
                exitReceived = true
            }

            launch {
                clientConnection.outgoing.collect { bytes ->
                    serverConnection.receive(bytes)
                }
            }
            launch {
                serverConnection.outgoing.collect { bytes ->
                    clientConnection.receive(bytes)
                }
            }

            val shutdownResult =
                clientConnection.request<JsonElement?, JsonElement?>(
                    Methods.BUILD_SHUTDOWN,
                    null,
                )

            shutdownResult shouldBe null
            shutdownReceived shouldBe true

            clientConnection.notify(Methods.BUILD_EXIT, null as JsonElement?)

            delay(100)
            exitReceived shouldBe true

            clientConnection.close()
            serverConnection.close()
            scope.cancel()
        }
}
