package io.bsp4k.example

import io.bsp4k.protocol.BuildTarget
import io.bsp4k.protocol.BuildTargetCapabilities
import io.bsp4k.protocol.BuildTargetIdentifier
import io.bsp4k.protocol.CleanCacheResult
import io.bsp4k.protocol.CompileParams
import io.bsp4k.protocol.CompileProvider
import io.bsp4k.protocol.CompileReport
import io.bsp4k.protocol.CompileResult
import io.bsp4k.protocol.CompileTask
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
import io.bsp4k.protocol.TaskFinishDataKind
import io.bsp4k.protocol.TaskStartDataKind
import io.bsp4k.protocol.TestParams
import io.bsp4k.protocol.TestProvider
import io.bsp4k.protocol.TestReport
import io.bsp4k.protocol.TestResult
import io.bsp4k.protocol.TestTask
import io.bsp4k.protocol.TextDocumentIdentifier
import io.bsp4k.protocol.WorkspaceBuildTargetsResult
import io.bsp4k.server.BuildServerConfig
import io.bsp4k.server.BuildServerContext
import io.bsp4k.server.buildServer
import io.bsp4k.server.requestTaskStart
import io.bsp4k.server.start
import io.bsp4k.server.withData
import io.jsonrpc4k.transport.TransportFactory
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

/**
 * An example "file-based build server" demonstrating bsp4k features.
 *
 * This server simulates a build system with two targets (main + test) and
 * demonstrates:
 * - Lifecycle hooks, including ending the process on `build/exit`
 * - Workspace target discovery and source listing
 * - Compile and test reported as typed tasks that echo the request's originId
 * - Run output forwarded with `run/printStdout`
 * - Clean cache and reload
 *
 * Run via: `./gradlew :example:run`
 */

private val mainTarget = BuildTargetIdentifier(uri = "file:///project/main")
private val testTarget = BuildTargetIdentifier(uri = "file:///project/test")

fun createExampleBuildServer(): BuildServerConfig =
    buildServer {
        serverInfo("example-build-server", "0.1.0")

        // Each provider needs its handler below, and each gated handler needs its provider.
        capabilities {
            compileProvider = CompileProvider(languageIds = listOf("kotlin"))
            testProvider = TestProvider(languageIds = listOf("kotlin"))
            runProvider = RunProvider(languageIds = listOf("kotlin"))
            canReload = true
        }

        lifecycle {
            onInitialized { client.logMessage(MessageType.Info, "Example Build Server initialized") }
            // BSP asks the server process to exit; the session flushed its last messages before this runs.
            onExit { exitCode -> exitProcess(exitCode) }
        }

        workspace {
            buildTargets { discoverTargets() }
            reload { client.logMessage(MessageType.Info, "Workspace reloaded") }
        }

        buildTarget {
            sources { params -> handleSources(params) }
            compile { params -> handleCompile(params) }
            test { params -> handleTest(params) }
            run { params ->
                val originId = params.originId
                client.logMessage(MessageType.Info, "Running target: ${params.target.uri}")
                // run/printStdout needs the originId of the request.
                if (originId != null) {
                    client.withTask(requestTaskStart("run-$originId", originId)) { printStdout("Hello from main") }
                }
                RunResult(originId = originId, statusCode = StatusCode.Ok)
            }
            cleanCache { params ->
                client.logMessage(MessageType.Info, "Cleaning cache for ${params.targets.size} target(s)")
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

/** Compiles each target as a child task carrying `compile-task` and `compile-report` data. */
private suspend fun BuildServerContext.handleCompile(params: CompileParams): CompileResult {
    val message = "Compiling ${params.targets.size} target(s)"
    val start = requestTaskStart("compile-${params.originId ?: "auto"}", params.originId, message)
    return client.withTask(start) {
        params.targets.forEach { target ->
            val child = childTask("compile-${target.uri}").withData(TaskStartDataKind.compileTask, CompileTask(target))
            client.withTask(child) {
                client.publishDiagnostics(
                    PublishDiagnosticsParams(
                        textDocument = TextDocumentIdentifier(uri = "file:///project/src/main/kotlin/App.kt"),
                        buildTarget = target,
                        originId = originId,
                        diagnostics =
                            listOf(
                                Diagnostic(
                                    range = Range(Position(line = 10, character = 4), Position(line = 10, character = 20)),
                                    severity = DiagnosticSeverity.Warning,
                                    source = "example-compiler",
                                    message = "Unused variable 'temp'",
                                ),
                            ),
                        reset = true,
                    ),
                )
                val report = CompileReport(target = target, originId = originId, errors = 0, warnings = 1)
                finish(StatusCode.Ok, TaskFinishDataKind.compileReport, report)
            }
        }
        CompileResult(originId = params.originId, statusCode = StatusCode.Ok)
    }
}

/** Runs tests as one task carrying `test-task` and `test-report` data. */
private suspend fun BuildServerContext.handleTest(params: TestParams): TestResult {
    val target = params.targets.first()
    val start =
        requestTaskStart("test-${params.originId ?: "auto"}", params.originId, "Running tests")
            .withData(TaskStartDataKind.testTask, TestTask(target))
    return client.withTask(start) {
        val report = TestReport(target, originId, passed = 1, failed = 0, ignored = 0, cancelled = 0, skipped = 0)
        finish(StatusCode.Ok, TaskFinishDataKind.testReport, report)
        TestResult(originId = params.originId, statusCode = StatusCode.Ok)
    }
}

fun main(): Unit =
    runBlocking {
        val server = createExampleBuildServer().start(TransportFactory.stdio(), this)
        server.awaitTermination()
    }
