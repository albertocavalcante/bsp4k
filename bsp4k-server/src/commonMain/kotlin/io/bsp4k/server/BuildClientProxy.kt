package io.bsp4k.server

import io.bsp4k.protocol.BspMethods
import io.bsp4k.protocol.DidChangeBuildTarget
import io.bsp4k.protocol.LogMessageParams
import io.bsp4k.protocol.MessageType
import io.bsp4k.protocol.PrintParams
import io.bsp4k.protocol.PublishDiagnosticsParams
import io.bsp4k.protocol.ShowMessageParams
import io.bsp4k.protocol.StatusCode
import io.bsp4k.protocol.TaskFinishParams
import io.bsp4k.protocol.TaskProgressParams
import io.bsp4k.protocol.TaskStartParams
import io.jsonrpc4k.core.Connection
import io.jsonrpc4k.core.JsonRpcCodec
import kotlinx.serialization.KSerializer

/**
 * Sends the server-to-client notifications of BSP 2.2, which defines no server-to-client requests.
 *
 * Available as [BuildServer.client] and, inside handlers and hooks, as [BuildServerContext.client]. Sending
 * does not check [BuildServerState]; BSP clients expect notifications only after the initialize result.
 */
public class BuildClientProxy internal constructor(
    private val connection: Connection,
) {
    /** Sends `build/showMessage`. */
    public suspend fun showMessage(params: ShowMessageParams) {
        send(BspMethods.BUILD_SHOW_MESSAGE, ShowMessageParams.serializer(), params)
    }

    /** Sends `build/showMessage` with the given [type] and [message]. */
    public suspend fun showMessage(
        type: MessageType,
        message: String,
    ) {
        showMessage(ShowMessageParams(type = type, message = message))
    }

    /** Sends `build/logMessage`. */
    public suspend fun logMessage(params: LogMessageParams) {
        send(BspMethods.BUILD_LOG_MESSAGE, LogMessageParams.serializer(), params)
    }

    /** Sends `build/logMessage` with the given [type] and [message]. */
    public suspend fun logMessage(
        type: MessageType,
        message: String,
    ) {
        logMessage(LogMessageParams(type = type, message = message))
    }

    /** Sends `build/publishDiagnostics`. */
    public suspend fun publishDiagnostics(params: PublishDiagnosticsParams) {
        send(BspMethods.BUILD_PUBLISH_DIAGNOSTICS, PublishDiagnosticsParams.serializer(), params)
    }

    /** Sends `buildTarget/didChange`; declare `buildTargetChangedProvider` when the server sends it. */
    public suspend fun buildTargetDidChange(params: DidChangeBuildTarget) {
        send(BspMethods.BUILD_TARGET_DID_CHANGE, DidChangeBuildTarget.serializer(), params)
    }

    /** Sends `build/taskStart`; [withTask] also guarantees the matching `build/taskFinish`. */
    public suspend fun taskStart(params: TaskStartParams) {
        send(BspMethods.BUILD_TASK_START, TaskStartParams.serializer(), params)
    }

    /** Sends `build/taskProgress`. */
    public suspend fun taskProgress(params: TaskProgressParams) {
        send(BspMethods.BUILD_TASK_PROGRESS, TaskProgressParams.serializer(), params)
    }

    /** Sends `build/taskFinish`. */
    public suspend fun taskFinish(params: TaskFinishParams) {
        send(BspMethods.BUILD_TASK_FINISH, TaskFinishParams.serializer(), params)
    }

    /** Sends `run/printStdout` (unstable in BSP 2.2): output of the process started by request [PrintParams.originId]. */
    public suspend fun printStdout(params: PrintParams) {
        send(BspMethods.RUN_PRINT_STDOUT, PrintParams.serializer(), params)
    }

    /** Sends `run/printStderr` (unstable in BSP 2.2): error output of the process started by request [PrintParams.originId]. */
    public suspend fun printStderr(params: PrintParams) {
        send(BspMethods.RUN_PRINT_STDERR, PrintParams.serializer(), params)
    }

    /**
     * Reports [block] as one BSP task and returns its result.
     *
     * Sends `build/taskStart` with [start], then runs [block] with a [BuildTask] whose notifications carry the
     * same task id and origin id. The task always finishes: when [block] returns without calling
     * [BuildTask.finish], this sends `build/taskFinish` with [StatusCode.Ok]; when it throws, this sends
     * [StatusCode.Error], or [StatusCode.Cancelled] for a cancellation, and rethrows. That final send is not
     * cancellable, so a request cancelled by `$/cancelRequest` still ends its task; a failure of the send is
     * added to the thrown exception as suppressed.
     */
    public suspend fun <R> withTask(
        start: TaskStartParams,
        block: suspend BuildTask.() -> R,
    ): R {
        taskStart(start)
        val task = BuildTask(this, start.taskId, start.originId)
        val result =
            try {
                task.block()
            } catch (failure: Throwable) {
                task.finishAfter(failure)
                throw failure
            }
        task.finishIfOpen(StatusCode.Ok)
        return result
    }

    private suspend fun <P> send(
        method: String,
        serializer: KSerializer<P>,
        params: P,
    ) {
        connection.notify(method, JsonRpcCodec.defaultJson.encodeToJsonElement(serializer, params))
    }
}
