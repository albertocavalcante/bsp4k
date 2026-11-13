package io.bsp4k.server

import io.bsp4k.protocol.BspDataKind
import io.bsp4k.protocol.BspMethods
import io.bsp4k.protocol.BuildTargetIdentifier
import io.bsp4k.protocol.CompileParams
import io.bsp4k.protocol.CompileProvider
import io.bsp4k.protocol.CompileReport
import io.bsp4k.protocol.CompileResult
import io.bsp4k.protocol.CompileTask
import io.bsp4k.protocol.LogMessageParams
import io.bsp4k.protocol.MessageType
import io.bsp4k.protocol.PrintParams
import io.bsp4k.protocol.StatusCode
import io.bsp4k.protocol.TaskFinishDataKind
import io.bsp4k.protocol.TaskFinishParams
import io.bsp4k.protocol.TaskId
import io.bsp4k.protocol.TaskProgressParams
import io.bsp4k.protocol.TaskStartDataKind
import io.bsp4k.protocol.TaskStartParams
import io.bsp4k.protocol.TestReport
import io.bsp4k.protocol.TestTask
import io.bsp4k.protocol.decodeData
import io.jsonrpc4k.core.BoundedDispatchPolicy
import io.jsonrpc4k.core.InboundResourceLimits
import io.jsonrpc4k.core.NotificationMessage
import io.jsonrpc4k.core.OutboundResourceLimits
import io.jsonrpc4k.core.RequestId
import io.jsonrpc4k.core.ResponseError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BuildTaskTest {
    private val target = BuildTargetIdentifier("file:///workspace/app")

    private suspend fun <P> TestClient.next(
        method: String,
        serializer: KSerializer<P>,
    ): P {
        val notification: NotificationMessage = notification()
        assertEquals(method, notification.method)
        return testJson.decodeFromJsonElement(serializer, assertNotNull(notification.params))
    }

    private suspend fun TestClient.nextStart(): TaskStartParams =
        next(BspMethods.BUILD_TASK_START, TaskStartParams.serializer())

    private suspend fun TestClient.nextFinish(): TaskFinishParams =
        next(BspMethods.BUILD_TASK_FINISH, TaskFinishParams.serializer())

    @Test
    fun `a compile handler reports a typed task that echoes its origin`() =
        runTest {
            val (_, peer) =
                buildServer {
                    capabilities { compileProvider = CompileProvider(listOf("kotlin")) }
                    buildTarget {
                        compile { params ->
                            val start =
                                requestTaskStart("compile-1", params.originId, "Compiling")
                                    .withData(TaskStartDataKind.compileTask, CompileTask(params.targets.single()))
                            client.withTask(start) {
                                progress(message = "half", progress = 1, total = 2, unit = "files")
                                logMessage(MessageType.Info, "compiling")
                                val report = CompileReport(params.targets.single(), originId, errors = 0, warnings = 1)
                                finish(StatusCode.Ok, TaskFinishDataKind.compileReport, report)
                                CompileResult(originId = originId, statusCode = StatusCode.Ok)
                            }
                        }
                    }
                }.connect(backgroundScope)
            peer.handshake()

            val params = encode(CompileParams.serializer(), CompileParams(listOf(target), originId = "o-1"))
            val response = peer.request(BspMethods.BUILD_TARGET_COMPILE, params)

            assertEquals(encode(CompileResult.serializer(), CompileResult("o-1", StatusCode.Ok)), response.result)
            val start = peer.nextStart()
            assertEquals(TaskId("compile-1", parents = listOf("o-1")), start.taskId)
            assertEquals("o-1", start.originId)
            assertEquals("Compiling", start.message)
            assertEquals(CompileTask(target), start.decodeData(TaskStartDataKind.compileTask))
            val progress = peer.next(BspMethods.BUILD_TASK_PROGRESS, TaskProgressParams.serializer())
            val expectedProgress =
                TaskProgressParams(start.taskId, "o-1", message = "half", total = 2, progress = 1, unit = "files")
            assertEquals(expectedProgress, progress)
            val log = peer.next(BspMethods.BUILD_LOG_MESSAGE, LogMessageParams.serializer())
            assertEquals(LogMessageParams(MessageType.Info, start.taskId, "o-1", "compiling"), log)
            val finish = peer.nextFinish()
            assertEquals(StatusCode.Ok, finish.status)
            assertEquals("o-1", finish.originId)
            val report = finish.decodeData(TaskFinishDataKind.compileReport)
            assertEquals(CompileReport(target, "o-1", errors = 0, warnings = 1), report)
            assertEquals(emptyList(), peer.pendingNotifications())
        }

    @Test
    fun `a task that returns without finishing ends with Ok`() =
        runTest {
            val (server, peer) = buildServer {}.connect(backgroundScope)
            peer.handshake()

            val result = server.client.withTask(requestTaskStart("plain", originId = null)) { 42 }

            assertEquals(42, result)
            assertEquals(TaskStartParams(TaskId("plain")), peer.nextStart())
            assertEquals(TaskFinishParams(TaskId("plain"), status = StatusCode.Ok), peer.nextFinish())
        }

    @Test
    fun `a failing task ends with Error and rethrows`() =
        runTest {
            val (server, peer) = buildServer {}.connect(backgroundScope)
            peer.handshake()

            val failure =
                assertFailsWith<IllegalStateException> {
                    server.client.withTask(requestTaskStart("broken", "o-2")) { error("boom") }
                }

            assertEquals("boom", failure.message)
            peer.nextStart()
            assertEquals(StatusCode.Error, peer.nextFinish().status)
        }

    @Test
    fun `a cancelled task ends with Cancelled and rethrows`() =
        runTest {
            val (server, peer) = buildServer {}.connect(backgroundScope)
            peer.handshake()

            assertFailsWith<CancellationException> {
                server.client.withTask(requestTaskStart("stopped", "o-3")) { throw CancellationException("stop") }
            }

            peer.nextStart()
            assertEquals(StatusCode.Cancelled, peer.nextFinish().status)
        }

    @Test
    fun `a task finishes once and refuses progress afterwards`() =
        runTest {
            val (server, peer) = buildServer {}.connect(backgroundScope)
            peer.handshake()

            server.client.withTask(requestTaskStart("once", "o-4")) {
                finish(StatusCode.Error, "first")
                assertTrue(isFinished)
                assertFailsWith<IllegalStateException> { finish(StatusCode.Ok) }
                assertFailsWith<IllegalStateException> { progress(message = "late") }
            }
            server.client.logMessage(MessageType.Log, "after")

            peer.nextStart()
            val finish = TaskFinishParams(TaskId("once", listOf("o-4")), "o-4", message = "first", status = StatusCode.Error)
            assertEquals(finish, peer.nextFinish())
            assertEquals("after", peer.next(BspMethods.BUILD_LOG_MESSAGE, LogMessageParams.serializer()).message)
        }

    @Test
    fun `a child task lists its parent and shares the origin`() =
        runTest {
            val (server, peer) = buildServer {}.connect(backgroundScope)
            peer.handshake()

            server.client.withTask(requestTaskStart("parent", "o-5")) {
                client.withTask(childTask("child", "step").withData(TaskStartDataKind.testTask, TestTask(target))) {
                    finish(StatusCode.Ok, TaskFinishDataKind.testReport, TestReport(target, originId, 1, 0, 0, 0, 0))
                }
            }

            assertEquals(TaskId("parent", listOf("o-5")), peer.nextStart().taskId)
            val child = peer.nextStart()
            assertEquals(TaskId("child", listOf("parent")), child.taskId)
            assertEquals("o-5", child.originId)
            assertEquals(TestTask(target), child.decodeData(TaskStartDataKind.testTask))
            val childFinish = peer.nextFinish()
            assertEquals(TaskId("child", listOf("parent")), childFinish.taskId)
            assertEquals(TestReport(target, "o-5", 1, 0, 0, 0, 0), childFinish.decodeData(TaskFinishDataKind.testReport))
            assertEquals(TaskId("parent", listOf("o-5")), peer.nextFinish().taskId)
        }

    @Test
    fun `print helpers echo the origin and need one`() =
        runTest {
            val (server, peer) = buildServer {}.connect(backgroundScope)
            peer.handshake()

            server.client.withTask(requestTaskStart("run", "o-6")) {
                printStdout("out")
                printStderr("err")
            }
            server.client.withTask(requestTaskStart("anonymous", originId = null)) {
                assertFailsWith<IllegalStateException> { printStdout("lost") }
            }

            peer.nextStart()
            val task = TaskId("run", listOf("o-6"))
            assertEquals(PrintParams("o-6", task, "out"), peer.next(BspMethods.RUN_PRINT_STDOUT, PrintParams.serializer()))
            assertEquals(PrintParams("o-6", task, "err"), peer.next(BspMethods.RUN_PRINT_STDERR, PrintParams.serializer()))
            peer.nextFinish()
            peer.nextStart()
            assertEquals(StatusCode.Ok, peer.nextFinish().status)
            assertEquals(emptyList(), peer.pendingNotifications())
        }

    @Test
    fun `withData builders set the kind and encode the payload`() {
        val taskId = TaskId("t")
        val start = TaskStartParams(taskId).withData(TaskStartDataKind.testTask, TestTask(target))
        val report = TestReport(target, null, passed = 2, failed = 1, ignored = 0, cancelled = 0, skipped = 0)
        val finish = TaskFinishParams(taskId, status = StatusCode.Error).withData(TaskFinishDataKind.testReport, report)
        val counted = BspDataKind<TaskProgressParams, Int>("counted", Int.serializer())
        val progress = TaskProgressParams(taskId).withData(counted, 3)

        assertEquals("test-task", start.dataKind)
        assertEquals(TestTask(target), start.decodeData(TaskStartDataKind.testTask))
        assertEquals("test-report", finish.dataKind)
        assertEquals(report, finish.decodeData(TaskFinishDataKind.testReport))
        assertNull(finish.decodeData(TaskFinishDataKind.compileReport))
        assertEquals("counted", progress.dataKind)
        assertEquals(JsonPrimitive(3), progress.data)
    }

    @Test
    fun `a request cancelled in a bounded session still ends its task`() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            val transport = MemoryTransport()
            val server =
                buildServer {
                    capabilities { compileProvider = CompileProvider(listOf("kotlin")) }
                    buildTarget {
                        compile { params ->
                            client.withTask(requestTaskStart("slow", params.originId)) {
                                entered.complete(Unit)
                                awaitCancellation()
                            }
                        }
                    }
                }.start(
                    transport,
                    BoundedDispatchPolicy(2, 8),
                    InboundResourceLimits(4096, 32, 8192),
                    OutboundResourceLimits(1024, 2048, 8, 8, 1024, 2, 1024, 32),
                    backgroundScope,
                )
            val peer = TestClient(transport)
            peer.handshake()

            val params = encode(CompileParams.serializer(), CompileParams(listOf(target), originId = "o-7"))
            val id = peer.sendRequest(BspMethods.BUILD_TARGET_COMPILE, params)
            withTimeout(5_000) { entered.await() }
            val number = assertIs<RequestId.NumberId>(id).value
            peer.notify(BspMethods.CANCEL_REQUEST, JsonObject(mapOf("id" to JsonPrimitive(number))))

            assertError(ResponseError.REQUEST_CANCELLED, peer.response(id))
            assertEquals(TaskId("slow", listOf("o-7")), peer.nextStart().taskId)
            assertEquals(StatusCode.Cancelled, peer.nextFinish().status)
            server.close()
        }
}
