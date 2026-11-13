package io.bsp4k.server

import io.bsp4k.protocol.BspMethods
import io.bsp4k.protocol.BuildTargetEvent
import io.bsp4k.protocol.BuildTargetEventKind
import io.bsp4k.protocol.BuildTargetIdentifier
import io.bsp4k.protocol.Diagnostic
import io.bsp4k.protocol.DiagnosticSeverity
import io.bsp4k.protocol.DidChangeBuildTarget
import io.bsp4k.protocol.LogMessageParams
import io.bsp4k.protocol.MessageType
import io.bsp4k.protocol.Position
import io.bsp4k.protocol.PrintParams
import io.bsp4k.protocol.PublishDiagnosticsParams
import io.bsp4k.protocol.Range
import io.bsp4k.protocol.ShowMessageParams
import io.bsp4k.protocol.StatusCode
import io.bsp4k.protocol.TaskFinishParams
import io.bsp4k.protocol.TaskId
import io.bsp4k.protocol.TaskProgressParams
import io.bsp4k.protocol.TaskStartParams
import io.bsp4k.protocol.TextDocumentIdentifier
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every BSP 2.2 server-to-client notification has a call on [BuildClientProxy] that reaches the wire intact. */
class ClientProxyTest {
    private suspend fun <P> TestClient.expectNotification(
        method: String,
        serializer: KSerializer<P>,
        params: P,
    ) {
        val notification = notification()
        assertEquals(method, notification.method)
        assertEquals(encode(serializer, params), notification.params, method)
    }

    @Test
    fun `every server notification goes out with its method and params`() =
        runTest {
            val target = BuildTargetIdentifier("file:///workspace/app")
            val task = TaskId("compile-1", parents = listOf("origin-1"))
            val show = ShowMessageParams(MessageType.Warning, task, "origin-1", "careful")
            val log = LogMessageParams(MessageType.Log, task, "origin-1", "detail")
            val diagnostic = Diagnostic(Range(Position(1, 2), Position(1, 5)), DiagnosticSeverity.Error, message = "bad")
            val diagnostics =
                PublishDiagnosticsParams(
                    textDocument = TextDocumentIdentifier("file:///workspace/app/A.kt"),
                    buildTarget = target,
                    originId = "origin-1",
                    diagnostics = listOf(diagnostic),
                    reset = true,
                )
            val changed = DidChangeBuildTarget(listOf(BuildTargetEvent(target, BuildTargetEventKind.Changed)))
            val start = TaskStartParams(task, "origin-1", eventTime = 10, message = "start")
            val progress = TaskProgressParams(task, "origin-1", total = 4, progress = 1, unit = "files")
            val finish = TaskFinishParams(task, "origin-1", message = "done", status = StatusCode.Ok)
            val stdout = PrintParams(originId = "origin-1", task = task, message = "out")
            val stderr = PrintParams(originId = "origin-1", message = "err")
            val shown = ShowMessageParams(MessageType.Info, message = "shown")
            val logged = LogMessageParams(MessageType.Error, message = "logged")
            val (server, peer) = buildServer {}.connect(backgroundScope)
            peer.handshake()

            server.client.showMessage(show)
            server.client.showMessage(MessageType.Info, "shown")
            server.client.logMessage(log)
            server.client.logMessage(MessageType.Error, "logged")
            server.client.publishDiagnostics(diagnostics)
            server.client.buildTargetDidChange(changed)
            server.client.taskStart(start)
            server.client.taskProgress(progress)
            server.client.taskFinish(finish)
            server.client.printStdout(stdout)
            server.client.printStderr(stderr)

            peer.expectNotification(BspMethods.BUILD_SHOW_MESSAGE, ShowMessageParams.serializer(), show)
            peer.expectNotification(BspMethods.BUILD_SHOW_MESSAGE, ShowMessageParams.serializer(), shown)
            peer.expectNotification(BspMethods.BUILD_LOG_MESSAGE, LogMessageParams.serializer(), log)
            peer.expectNotification(BspMethods.BUILD_LOG_MESSAGE, LogMessageParams.serializer(), logged)
            peer.expectNotification(BspMethods.BUILD_PUBLISH_DIAGNOSTICS, PublishDiagnosticsParams.serializer(), diagnostics)
            peer.expectNotification(BspMethods.BUILD_TARGET_DID_CHANGE, DidChangeBuildTarget.serializer(), changed)
            peer.expectNotification(BspMethods.BUILD_TASK_START, TaskStartParams.serializer(), start)
            peer.expectNotification(BspMethods.BUILD_TASK_PROGRESS, TaskProgressParams.serializer(), progress)
            peer.expectNotification(BspMethods.BUILD_TASK_FINISH, TaskFinishParams.serializer(), finish)
            peer.expectNotification(BspMethods.RUN_PRINT_STDOUT, PrintParams.serializer(), stdout)
            peer.expectNotification(BspMethods.RUN_PRINT_STDERR, PrintParams.serializer(), stderr)
        }

    @Test
    fun `handlers reach the client through their context`() =
        runTest {
            val (_, peer) =
                buildServer {
                    onRequest("custom/echo") { params ->
                        client.logMessage(MessageType.Info, "echo")
                        params
                    }
                }.connect(backgroundScope)
            peer.handshake()

            assertEquals(null, peer.request("custom/echo").error)

            val echo = LogMessageParams(MessageType.Info, message = "echo")
            peer.expectNotification(BspMethods.BUILD_LOG_MESSAGE, LogMessageParams.serializer(), echo)
        }
}
