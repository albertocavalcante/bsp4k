package io.bsp4k.server.e2e

import io.bsp4k.client.BuildClientSession
import io.bsp4k.client.SessionState
import io.bsp4k.client.SessionStateException
import io.bsp4k.client.buildClient
import io.bsp4k.client.start
import io.bsp4k.protocol.BSP_VERSION
import io.bsp4k.protocol.BuildTarget
import io.bsp4k.protocol.BuildTargetCapabilities
import io.bsp4k.protocol.BuildTargetEvent
import io.bsp4k.protocol.BuildTargetEventKind
import io.bsp4k.protocol.BuildTargetIdentifier
import io.bsp4k.protocol.CompileParams
import io.bsp4k.protocol.CompileProvider
import io.bsp4k.protocol.CompileReport
import io.bsp4k.protocol.CompileResult
import io.bsp4k.protocol.CompileTask
import io.bsp4k.protocol.DebugProvider
import io.bsp4k.protocol.DebugSessionAddress
import io.bsp4k.protocol.DebugSessionParams
import io.bsp4k.protocol.Destination
import io.bsp4k.protocol.DestinationIdentifier
import io.bsp4k.protocol.DestinationsParams
import io.bsp4k.protocol.DestinationsResult
import io.bsp4k.protocol.DidChangeBuildTarget
import io.bsp4k.protocol.LogMessageParams
import io.bsp4k.protocol.MessageType
import io.bsp4k.protocol.PrintParams
import io.bsp4k.protocol.ReadParams
import io.bsp4k.protocol.RunParams
import io.bsp4k.protocol.RunProvider
import io.bsp4k.protocol.RunResult
import io.bsp4k.protocol.ScalacOptionsItem
import io.bsp4k.protocol.ScalacOptionsParams
import io.bsp4k.protocol.ScalacOptionsResult
import io.bsp4k.protocol.SourceItem
import io.bsp4k.protocol.SourceItemKind
import io.bsp4k.protocol.SourcesItem
import io.bsp4k.protocol.SourcesParams
import io.bsp4k.protocol.SourcesResult
import io.bsp4k.protocol.StatusCode
import io.bsp4k.protocol.TaskFinishDataKind
import io.bsp4k.protocol.TaskFinishParams
import io.bsp4k.protocol.TaskStartDataKind
import io.bsp4k.protocol.TaskStartParams
import io.bsp4k.protocol.WorkspaceBuildTargetsResult
import io.bsp4k.server.BuildServer
import io.bsp4k.server.BuildServerState
import io.bsp4k.server.buildServer
import io.bsp4k.server.requestTaskStart
import io.bsp4k.server.start
import io.bsp4k.server.withData
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private val main = BuildTargetIdentifier(uri = "file:///project/main")
private val jvm17 = DestinationIdentifier(uri = "jvm:17")
private val sourceFile = SourceItem(uri = "file:///project/src/Main.kt", kind = SourceItemKind.File, generated = false)
private val debugAddress = DebugSessionAddress(uri = "tcp://127.0.0.1:5005")

/** Everything the server side observed, filled in by the server's handlers. */
private class ServerSeen {
    val stdin = Channel<ReadParams>(Channel.UNLIMITED)
    val exitCodes = mutableListOf<Int>()
    val runParams = mutableListOf<RunParams>()
    val debugParams = mutableListOf<DebugSessionParams>()
}

/** Everything the client side received, filled in by the client's hooks. */
private class ClientSeen {
    val logs = Channel<LogMessageParams>(Channel.UNLIMITED)
    val taskStarts = Channel<TaskStartParams>(Channel.UNLIMITED)
    val taskFinishes = Channel<TaskFinishParams>(Channel.UNLIMITED)
    val stdout = Channel<PrintParams>(Channel.UNLIMITED)
    val changes = Channel<DidChangeBuildTarget>(Channel.UNLIMITED)
}

private suspend fun <T> Channel<T>.next(): T = withTimeout(5.seconds) { receive() }

/** A real `buildServer { }` session and a real `buildClient { }` session joined by a [FramedPipe]. */
private class EndToEnd(
    val server: BuildServer,
    val session: BuildClientSession,
    val serverSeen: ServerSeen,
    val clientSeen: ClientSeen,
)

private fun serverConfig(seen: ServerSeen) =
    buildServer {
        serverInfo("e2e-server", "1.2.3")
        capabilities {
            compileProvider = CompileProvider(languageIds = listOf("kotlin"))
            runProvider = RunProvider(languageIds = listOf("kotlin"))
            debugProvider = DebugProvider(languageIds = listOf("kotlin"))
            buildTargetDestinationsProvider = true
            buildTargetChangedProvider = true
        }
        lifecycle {
            onInitialized { client.logMessage(MessageType.Info, "ready") }
            onExit { exitCode -> seen.exitCodes += exitCode }
        }
        workspace {
            buildTargets {
                WorkspaceBuildTargetsResult(
                    targets =
                        listOf(
                            BuildTarget(
                                id = main,
                                displayName = "main",
                                tags = listOf("application"),
                                capabilities = BuildTargetCapabilities(canCompile = true, canRun = true, canDebug = true),
                                languageIds = listOf("kotlin"),
                                dependencies = emptyList(),
                            ),
                        ),
                )
            }
        }
        buildTarget {
            sources { params ->
                SourcesResult(
                    items =
                        params.targets.map { target ->
                            SourcesItem(
                                target = target,
                                sources = listOf(sourceFile),
                            )
                        },
                )
            }
            destinations { params ->
                val destination = Destination(id = jvm17, displayName = "JVM 17 for ${params.target.uri}")
                DestinationsResult(destinations = listOf(destination))
            }
            compile { params ->
                val target = params.targets.single()
                val start =
                    requestTaskStart("compile", params.originId, "compiling")
                        .withData(TaskStartDataKind.compileTask, CompileTask(target))
                client.withTask(start) {
                    finish(StatusCode.Ok, TaskFinishDataKind.compileReport, CompileReport(target, originId, errors = 0, warnings = 0))
                    CompileResult(originId = params.originId, statusCode = StatusCode.Ok)
                }
            }
            run { params ->
                seen.runParams += params
                client.withTask(requestTaskStart("run", params.originId)) {
                    printStdout("hello ")
                    printStdout("world")
                }
                RunResult(originId = params.originId, statusCode = StatusCode.Ok)
            }
        }
        debugSession {
            start { params ->
                seen.debugParams += params
                debugAddress
            }
        }
        run {
            readStdin { params -> seen.stdin.send(params) }
        }
        scala {
            scalacOptions { params ->
                ScalacOptionsResult(
                    items =
                        params.targets.map { target ->
                            ScalacOptionsItem(
                                target = target,
                                options = listOf("-deprecation"),
                                classpath = listOf("lib.jar"),
                                classDirectory = "file:///out",
                            )
                        },
                )
            }
        }
    }

private suspend fun TestScope.startEndToEnd(initialize: Boolean = true): EndToEnd {
    val pipe = FramedPipe()
    val serverSeen = ServerSeen()
    val clientSeen = ClientSeen()
    val server = serverConfig(serverSeen).start(pipe.server, backgroundScope)
    val client =
        buildClient {
            clientInfo("e2e-client", "4.5.6")
            rootUri("file:///project")
            capabilities(languageIds = listOf("kotlin"))
            onLogMessage { clientSeen.logs.send(it) }
            onTaskStart { clientSeen.taskStarts.send(it) }
            onTaskFinish { clientSeen.taskFinishes.send(it) }
            onPrintStdout { clientSeen.stdout.send(it) }
            onBuildTargetDidChange { clientSeen.changes.send(it) }
        }
    val session = client.start(pipe.client, backgroundScope)
    if (initialize) session.initialize()
    return EndToEnd(server, session, serverSeen, clientSeen)
}

class BspEndToEndTest {
    @Test
    fun `initialize and initialized bring both sessions to running`() =
        runTest {
            val e2e = startEndToEnd(initialize = false)
            val result = e2e.session.initialize()

            assertEquals("e2e-server", result.displayName)
            assertEquals("1.2.3", result.version)
            assertEquals(BSP_VERSION, result.bspVersion)
            assertTrue(result.capabilities.buildTargetChangedProvider)
            assertEquals(SessionState.READY, e2e.session.state)
            withTimeout(5.seconds) { e2e.server.state.first { it == BuildServerState.Running } }
            assertEquals(
                "ready",
                e2e.clientSeen.logs
                    .next()
                    .message,
            )
        }

    @Test
    fun `workspace buildTargets and sources travel the framed wire`() =
        runTest {
            val e2e = startEndToEnd()
            val targets =
                e2e.session.server
                    .workspaceBuildTargets()
                    .targets
            assertEquals(listOf(main), targets.map { it.id })
            assertEquals("main", targets.single().displayName)

            val sources = e2e.session.server.sources(SourcesParams(targets = listOf(main)))
            assertEquals(
                sourceFile,
                sources.items
                    .single()
                    .sources
                    .single(),
            )
        }

    @Test
    fun `compile reports a task started and finished with the request originId`() =
        runTest {
            val e2e = startEndToEnd()
            val result = e2e.session.server.compile(CompileParams(targets = listOf(main), originId = "origin-7"))

            assertEquals(StatusCode.Ok, result.statusCode)
            assertEquals("origin-7", result.originId)
            val start = e2e.clientSeen.taskStarts.next()
            assertEquals("origin-7", start.originId)
            assertEquals("compile", start.taskId.id)
            assertEquals(TaskStartDataKind.compileTask.kind, start.dataKind)
            val finish = e2e.clientSeen.taskFinishes.next()
            assertEquals("origin-7", finish.originId)
            assertEquals(StatusCode.Ok, finish.status)
            assertEquals(TaskFinishDataKind.compileReport.kind, finish.dataKind)
        }

    @Test
    fun `run streams printStdout and delivers readStdin to the server`() =
        runTest {
            val e2e = startEndToEnd()
            val result = e2e.session.server.run(RunParams(target = main, destination = jvm17, originId = "run-1"))

            assertEquals(StatusCode.Ok, result.statusCode)
            assertEquals(
                jvm17,
                e2e.serverSeen.runParams
                    .single()
                    .destination,
            )
            val first = e2e.clientSeen.stdout.next()
            val second = e2e.clientSeen.stdout.next()
            assertEquals(listOf("hello ", "world"), listOf(first.message, second.message))
            assertEquals(listOf("run-1", "run-1"), listOf(first.originId, second.originId))
            assertEquals("run", first.task?.id)

            e2e.session.server.readStdin(ReadParams(originId = "run-1", message = "input\n"))
            val input = e2e.serverSeen.stdin.next()
            assertEquals("run-1", input.originId)
            assertEquals("input\n", input.message)
        }

    @Test
    fun `debugSession start returns the address chosen by the server`() =
        runTest {
            val e2e = startEndToEnd()
            val address = e2e.session.server.debugSession(DebugSessionParams(targets = listOf(main), destination = jvm17))

            assertEquals(debugAddress, address)
            assertEquals(
                jvm17,
                e2e.serverSeen.debugParams
                    .single()
                    .destination,
            )
        }

    @Test
    fun `destinations lists the places a target can run`() =
        runTest {
            val e2e = startEndToEnd()
            val result = e2e.session.server.destinations(DestinationsParams(target = main))

            assertEquals(jvm17, result.destinations.single().id)
        }

    @Test
    fun `scalacOptions extension request round trips`() =
        runTest {
            val e2e = startEndToEnd()
            val result =
                e2e.session.server.scala
                    .scalacOptions(ScalacOptionsParams(targets = listOf(main)))

            val item = result.items.single()
            assertEquals(main, item.target)
            assertEquals(listOf("-deprecation"), item.options)
            assertEquals("file:///out", item.classDirectory)
        }

    @Test
    fun `buildTarget didChange pushed by the server reaches the client hook`() =
        runTest {
            val e2e = startEndToEnd()
            val change = DidChangeBuildTarget(listOf(BuildTargetEvent(target = main, kind = BuildTargetEventKind.Changed)))
            e2e.server.client.buildTargetDidChange(change)

            assertEquals(change, e2e.clientSeen.changes.next())
        }

    @Test
    fun `shutdown and exit end both sessions with exit code zero`() =
        runTest {
            val e2e = startEndToEnd()
            e2e.session.shutdown()

            withTimeout(5.seconds) { e2e.server.state.first { it == BuildServerState.Exited } }
            assertNull(withTimeout(5.seconds) { e2e.server.awaitTermination() })
            assertNull(withTimeout(5.seconds) { e2e.session.awaitTermination() })
            assertEquals(listOf(0), e2e.serverSeen.exitCodes)
            assertFailsWith<SessionStateException> { e2e.session.server.workspaceBuildTargets() }
        }
}
