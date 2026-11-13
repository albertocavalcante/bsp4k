package io.bsp4k.server

import io.bsp4k.protocol.BspDataKind
import io.bsp4k.protocol.LogMessageParams
import io.bsp4k.protocol.MessageType
import io.bsp4k.protocol.PrintParams
import io.bsp4k.protocol.StatusCode
import io.bsp4k.protocol.TaskFinishParams
import io.bsp4k.protocol.TaskId
import io.bsp4k.protocol.TaskProgressParams
import io.bsp4k.protocol.TaskStartParams
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * One BSP task reported through [BuildClientProxy.withTask]. Every notification it sends carries [taskId]
 * and [originId], and the task finishes exactly once.
 *
 * @property client The proxy this task reports through; `client.withTask(childTask(id)) { }` runs a child task.
 * @property taskId The id shared by the task's start, progress and finish notifications.
 * @property originId The `originId` of the client request behind this task, or null when it sent none.
 */
public class BuildTask internal constructor(
    public val client: BuildClientProxy,
    public val taskId: TaskId,
    public val originId: String?,
) {
    private val finished = MutableStateFlow(false)

    /** Whether `build/taskFinish` was sent, or is being sent, for this task. */
    public val isFinished: Boolean
        get() = finished.value

    /** Sends `build/taskProgress`; fails with [IllegalStateException] once the task is finished. */
    public suspend fun progress(
        message: String? = null,
        progress: Long? = null,
        total: Long? = null,
        unit: String? = null,
    ) {
        check(!isFinished) { "Task ${taskId.id} is already finished" }
        client.taskProgress(
            TaskProgressParams(
                taskId = taskId,
                originId = originId,
                message = message,
                total = total,
                progress = progress,
                unit = unit,
            ),
        )
    }

    /** Sends `build/taskFinish` with [status]; fails with [IllegalStateException] when already finished. */
    public suspend fun finish(
        status: StatusCode,
        message: String? = null,
    ) {
        finishWith(finishParams(status, message))
    }

    /**
     * Sends `build/taskFinish` with [status] and typed [data] of [kind], for example
     * `TaskFinishDataKind.compileReport`; fails with [IllegalStateException] when already finished.
     */
    public suspend fun <T> finish(
        status: StatusCode,
        kind: BspDataKind<TaskFinishParams, T>,
        data: T,
        message: String? = null,
    ) {
        finishWith(finishParams(status, message).withData(kind, data))
    }

    /** Sends `build/logMessage` attributed to this task and [originId]. */
    public suspend fun logMessage(
        type: MessageType,
        message: String,
    ) {
        client.logMessage(LogMessageParams(type = type, task = taskId, originId = originId, message = message))
    }

    /** Sends `run/printStdout` for this task; BSP requires an [originId], so this fails without one. */
    public suspend fun printStdout(message: String) {
        client.printStdout(printParams(message))
    }

    /** Sends `run/printStderr` for this task; BSP requires an [originId], so this fails without one. */
    public suspend fun printStderr(message: String) {
        client.printStderr(printParams(message))
    }

    /** Start params for a child task: its id lists this task as parent and it shares [originId]. */
    public fun childTask(
        id: String,
        message: String? = null,
    ): TaskStartParams =
        TaskStartParams(
            taskId = TaskId(id = id, parents = listOf(taskId.id)),
            originId = originId,
            message = message,
        )

    internal suspend fun finishIfOpen(status: StatusCode) {
        if (finished.compareAndSet(expect = false, update = true)) client.taskFinish(finishParams(status, null))
    }

    /** Ends the task after [failure] without letting cancellation skip the send. */
    internal suspend fun finishAfter(failure: Throwable) {
        if (!finished.compareAndSet(expect = false, update = true)) return
        val status = if (failure is CancellationException) StatusCode.Cancelled else StatusCode.Error
        try {
            withContext(NonCancellable) { client.taskFinish(finishParams(status, null)) }
        } catch (secondary: Exception) {
            if (secondary !== failure) failure.addSuppressed(secondary)
        }
    }

    private suspend fun finishWith(params: TaskFinishParams) {
        check(finished.compareAndSet(expect = false, update = true)) { "Task ${taskId.id} is already finished" }
        client.taskFinish(params)
    }

    private fun finishParams(
        status: StatusCode,
        message: String?,
    ): TaskFinishParams = TaskFinishParams(taskId = taskId, originId = originId, message = message, status = status)

    private fun printParams(message: String): PrintParams {
        val origin = checkNotNull(originId) { "run/printStdout and run/printStderr need the originId of a request" }
        return PrintParams(originId = origin, task = taskId, message = message)
    }
}

/**
 * Start params for a task spawned directly by a client request. BSP asks such a task to list the request's
 * [originId] as its parent, and [BuildClientProxy.withTask] echoes [originId] in every notification of the task.
 */
public fun requestTaskStart(
    id: String,
    originId: String?,
    message: String? = null,
): TaskStartParams =
    TaskStartParams(
        taskId = TaskId(id = id, parents = originId?.let { listOf(it) }),
        originId = originId,
        message = message,
    )

/** Returns a copy whose `data` is [value] encoded as [kind], for example `TaskStartDataKind.compileTask`. */
public fun <T> TaskStartParams.withData(
    kind: BspDataKind<TaskStartParams, T>,
    value: T,
): TaskStartParams = copy(dataKind = kind.kind, data = kind.encode(value))

/** Returns a copy whose `data` is [value] encoded as [kind]; BSP 2.2 defines no progress kinds of its own. */
public fun <T> TaskProgressParams.withData(
    kind: BspDataKind<TaskProgressParams, T>,
    value: T,
): TaskProgressParams = copy(dataKind = kind.kind, data = kind.encode(value))

/** Returns a copy whose `data` is [value] encoded as [kind], for example `TaskFinishDataKind.testReport`. */
public fun <T> TaskFinishParams.withData(
    kind: BspDataKind<TaskFinishParams, T>,
    value: T,
): TaskFinishParams = copy(dataKind = kind.kind, data = kind.encode(value))
