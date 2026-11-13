package io.bsp4k.client

import io.bsp4k.protocol.BspMethods
import io.bsp4k.protocol.BuildTargetEvent
import io.bsp4k.protocol.BuildTargetEventKind
import io.bsp4k.protocol.BuildTargetIdentifier
import io.bsp4k.protocol.DidChangeBuildTarget
import io.bsp4k.protocol.LogMessageParams
import io.bsp4k.protocol.MessageType
import io.bsp4k.protocol.PrintParams
import io.bsp4k.protocol.PublishDiagnosticsParams
import io.bsp4k.protocol.ShowMessageParams
import io.bsp4k.protocol.StatusCode
import io.bsp4k.protocol.TaskFinishParams
import io.bsp4k.protocol.TaskId
import io.bsp4k.protocol.TaskProgressParams
import io.bsp4k.protocol.TaskStartParams
import io.bsp4k.protocol.TextDocumentIdentifier
import io.kotest.matchers.maps.shouldContainKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationHooksTest {
    private suspend fun <T> TestScope.deliver(
        method: String,
        paramsJson: String,
        register: BuildClientBuilder.(suspend (T) -> Unit) -> Unit,
    ): T {
        val received = CompletableDeferred<T>()
        val peer = FakePeer()
        connect(peer, buildClient { register { received.complete(it) } })
        peer.push(method, paramsJson)
        return withTimeout(5_000) { received.await() }
    }

    @Test
    fun `onLogMessage receives build logMessage`() =
        runTest {
            val got = deliver<LogMessageParams>(BspMethods.BUILD_LOG_MESSAGE, """{"type":4,"message":"hi"}""") { onLogMessage(it) }
            assertEquals(LogMessageParams(MessageType.Log, message = "hi"), got)
        }

    @Test
    fun `onShowMessage receives build showMessage`() =
        runTest {
            val got = deliver<ShowMessageParams>(BspMethods.BUILD_SHOW_MESSAGE, """{"type":3,"message":"hi"}""") { onShowMessage(it) }
            assertEquals(ShowMessageParams(MessageType.Info, message = "hi"), got)
        }

    @Test
    fun `onPublishDiagnostics receives build publishDiagnostics`() =
        runTest {
            val json = """{"textDocument":{"uri":"d"},"buildTarget":{"uri":"t"},"diagnostics":[],"reset":true}"""
            val got = deliver<PublishDiagnosticsParams>(BspMethods.BUILD_PUBLISH_DIAGNOSTICS, json) { onPublishDiagnostics(it) }
            assertEquals(
                PublishDiagnosticsParams(TextDocumentIdentifier("d"), BuildTargetIdentifier("t"), diagnostics = emptyList(), reset = true),
                got,
            )
        }

    @Test
    fun `onBuildTargetDidChange receives buildTarget didChange`() =
        runTest {
            val json = """{"changes":[{"target":{"uri":"t"},"kind":2}]}"""
            val got = deliver<DidChangeBuildTarget>(BspMethods.BUILD_TARGET_DID_CHANGE, json) { onBuildTargetDidChange(it) }
            assertEquals(DidChangeBuildTarget(listOf(BuildTargetEvent(BuildTargetIdentifier("t"), BuildTargetEventKind.Changed))), got)
        }

    @Test
    fun `onTaskStart receives build taskStart`() =
        runTest {
            val got = deliver<TaskStartParams>(BspMethods.BUILD_TASK_START, """{"taskId":{"id":"1"},"message":"go"}""") { onTaskStart(it) }
            assertEquals(TaskStartParams(TaskId("1"), message = "go"), got)
        }

    @Test
    fun `onTaskProgress receives build taskProgress`() =
        runTest {
            val json = """{"taskId":{"id":"1"},"progress":1,"total":2}"""
            val got = deliver<TaskProgressParams>(BspMethods.BUILD_TASK_PROGRESS, json) { onTaskProgress(it) }
            assertEquals(TaskProgressParams(TaskId("1"), total = 2, progress = 1), got)
        }

    @Test
    fun `onTaskFinish receives build taskFinish`() =
        runTest {
            val got = deliver<TaskFinishParams>(BspMethods.BUILD_TASK_FINISH, """{"taskId":{"id":"1"},"status":1}""") { onTaskFinish(it) }
            assertEquals(TaskFinishParams(TaskId("1"), status = StatusCode.Ok), got)
        }

    @Test
    fun `onPrintStdout receives run printStdout`() =
        runTest {
            val got = deliver<PrintParams>(BspMethods.RUN_PRINT_STDOUT, """{"originId":"o","message":"out"}""") { onPrintStdout(it) }
            assertEquals(PrintParams(originId = "o", message = "out"), got)
        }

    @Test
    fun `onPrintStderr receives run printStderr`() =
        runTest {
            val got = deliver<PrintParams>(BspMethods.RUN_PRINT_STDERR, """{"originId":"o","message":"err"}""") { onPrintStderr(it) }
            assertEquals(PrintParams(originId = "o", message = "err"), got)
        }

    @Test
    fun `every server to client notification has a registered hook`() {
        val config =
            buildClient {
                onLogMessage {}
                onShowMessage {}
                onPublishDiagnostics {}
                onBuildTargetDidChange {}
                onTaskStart {}
                onTaskProgress {}
                onTaskFinish {}
                onPrintStdout {}
                onPrintStderr {}
            }
        listOf(
            BspMethods.BUILD_LOG_MESSAGE,
            BspMethods.BUILD_SHOW_MESSAGE,
            BspMethods.BUILD_PUBLISH_DIAGNOSTICS,
            BspMethods.BUILD_TARGET_DID_CHANGE,
            BspMethods.BUILD_TASK_START,
            BspMethods.BUILD_TASK_PROGRESS,
            BspMethods.BUILD_TASK_FINISH,
            BspMethods.RUN_PRINT_STDOUT,
            BspMethods.RUN_PRINT_STDERR,
        ).forEach { config.notificationHandlers shouldContainKey it }
    }
}
