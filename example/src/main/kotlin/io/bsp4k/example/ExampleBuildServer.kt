package io.bsp4k.example

import io.bsp4k.protocol.BuildTarget
import io.bsp4k.protocol.BuildTargetCapabilities
import io.bsp4k.protocol.BuildTargetIdentifier
import io.bsp4k.protocol.CleanCacheResult
import io.bsp4k.protocol.CompileParams
import io.bsp4k.protocol.CompileProvider
import io.bsp4k.protocol.CompileResult
import io.bsp4k.protocol.Diagnostic
import io.bsp4k.protocol.DiagnosticSeverity
import io.bsp4k.protocol.MessageType
import io.bsp4k.protocol.Position
import io.bsp4k.protocol.PublishDiagnosticsParams
import io.bsp4k.protocol.Range
import io.bsp4k.protocol.RunProvider
import io.bsp4k.protocol.RunResult
import io.bsp4k.protocol.SourceItem
import io.bsp4k.protocol.SourceItemKind
import io.bsp4k.protocol.SourcesItem
import io.bsp4k.protocol.SourcesParams
import io.bsp4k.protocol.SourcesResult
import io.bsp4k.protocol.StatusCode
import io.bsp4k.protocol.TaskFinishParams
import io.bsp4k.protocol.TaskId
import io.bsp4k.protocol.TaskStartParams
import io.bsp4k.protocol.TestParams
import io.bsp4k.protocol.TestProvider
import io.bsp4k.protocol.TestResult
import io.bsp4k.protocol.TextDocumentIdentifier
import io.bsp4k.protocol.WorkspaceBuildTargetsResult
import io.bsp4k.server.BuildServer
import io.bsp4k.server.buildServer
import io.bsp4k.server.start
import io.jsonrpc4k.transport.TransportFactory
import kotlinx.coroutines.runBlocking

/**
 * An example "file-based build server" demonstrating bsp4k features.
 *
 * This server simulates a build system with two targets (main + test) and
 * demonstrates:
 * - Workspace target discovery
 * - Source listing
 * - Compile with task progress notifications and diagnostics
 * - Test simulation with task progress
 * - Run, clean cache, reload
 *
 * Run via: `./gradlew :example:run`
 */

private val mainTarget = BuildTargetIdentifier(uri = "file:///project/main")
private val testTarget = BuildTargetIdentifier(uri = "file:///project/test")

private var serverInstance: BuildServer? = null

fun createExampleBuildServer() =
    buildServer {
        serverInfo("example-build-server", "0.1.0")

        capabilities {
            compileProvider = CompileProvider(languageIds = listOf("kotlin"))
            testProvider = TestProvider(languageIds = listOf("kotlin"))
            runProvider = RunProvider(languageIds = listOf("kotlin"))
            inverseSourcesProvider = true
            dependencySourcesProvider = true
            resourcesProvider = true
            canReload = true
        }

        workspace {
            buildTargets { discoverTargets() }
            reload { serverInstance?.client?.logMessage(MessageType.Info, "Workspace reloaded") }
        }

        buildTarget {
            sources { params -> handleSources(params) }
            compile { params -> handleCompile(params) }
            test { params -> handleTest(params) }
            run { params ->
                serverInstance?.client?.logMessage(MessageType.Info, "Running target: ${params.target.uri}")
                RunResult(originId = params.originId, statusCode = StatusCode.Ok)
            }
            cleanCache { params ->
                serverInstance?.client?.logMessage(MessageType.Info, "Cleaning cache for ${params.targets.size} target(s)")
                CleanCacheResult(message = "Cache cleaned", cleaned = true)
            }
        }
    }

private fun discoverTargets() =
    WorkspaceBuildTargetsResult(
        targets =
            listOf(
                BuildTarget(
                    id = mainTarget,
                    displayName = "main",
                    baseDirectory = "file:///project/src/main",
                    tags = listOf("application"),
                    capabilities = BuildTargetCapabilities(canCompile = true, canRun = true),
                    languageIds = listOf("kotlin"),
                    dependencies = emptyList(),
                ),
                BuildTarget(
                    id = testTarget,
                    displayName = "test",
                    baseDirectory = "file:///project/src/test",
                    tags = listOf("test"),
                    capabilities = BuildTargetCapabilities(canCompile = true, canTest = true),
                    languageIds = listOf("kotlin"),
                    dependencies = listOf(mainTarget),
                ),
            ),
    )

private fun handleSources(params: SourcesParams) =
    SourcesResult(
        items =
            params.targets.map { target ->
                when (target) {
                    mainTarget ->
                        SourcesItem(
                            target = target,
                            sources =
                                listOf(
                                    SourceItem(
                                        uri = "file:///project/src/main/kotlin/Main.kt",
                                        kind = SourceItemKind.File,
                                        generated = false,
                                    ),
                                    SourceItem(
                                        uri = "file:///project/src/main/kotlin/App.kt",
                                        kind = SourceItemKind.File,
                                        generated = false,
                                    ),
                                ),
                            roots = listOf("file:///project/src/main/kotlin"),
                        )
                    testTarget ->
                        SourcesItem(
                            target = target,
                            sources =
                                listOf(
                                    SourceItem(
                                        uri = "file:///project/src/test/kotlin/MainTest.kt",
                                        kind = SourceItemKind.File,
                                        generated = false,
                                    ),
                                ),
                            roots = listOf("file:///project/src/test/kotlin"),
                        )
                    else -> SourcesItem(target = target, sources = emptyList())
                }
            },
    )

private suspend fun handleCompile(params: CompileParams): CompileResult {
    val client = serverInstance?.client
    val taskId = TaskId(id = "compile-${params.originId ?: "auto"}")

    client?.taskStart(TaskStartParams(taskId = taskId, message = "Compiling ${params.targets.size} target(s)"))

    params.targets.forEach { target ->
        client?.publishDiagnostics(
            PublishDiagnosticsParams(
                textDocument = TextDocumentIdentifier(uri = "file:///project/src/main/kotlin/App.kt"),
                buildTarget = target,
                originId = params.originId,
                diagnostics =
                    listOf(
                        Diagnostic(
                            range = Range(Position(line = 10, character = 4), Position(line = 10, character = 20)),
                            severity = DiagnosticSeverity.Warning,
                            source = "example-compiler",
                            message = "Unused variable 'temp'",
                        ),
                    ),
                reset = false,
            ),
        )
    }

    client?.taskFinish(TaskFinishParams(taskId = taskId, status = StatusCode.Ok, message = "Compilation succeeded"))
    return CompileResult(originId = params.originId, statusCode = StatusCode.Ok)
}

private suspend fun handleTest(params: TestParams): TestResult {
    val client = serverInstance?.client
    val taskId = TaskId(id = "test-${params.originId ?: "auto"}")

    client?.taskStart(TaskStartParams(taskId = taskId, message = "Running tests"))
    client?.taskFinish(TaskFinishParams(taskId = taskId, status = StatusCode.Ok, message = "Tests passed"))

    return TestResult(originId = params.originId, statusCode = StatusCode.Ok)
}

fun main(): Unit =
    runBlocking {
        val config = createExampleBuildServer()
        val transport = TransportFactory.stdio()
        serverInstance = config.start(transport)
        serverInstance?.client?.logMessage(MessageType.Info, "Example Build Server started")
    }
