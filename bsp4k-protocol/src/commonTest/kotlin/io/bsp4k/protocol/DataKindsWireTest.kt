package io.bsp4k.protocol

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test

class DataKindsWireTest {
    private val target = BuildTargetIdentifier(uri = "file:///p/main")
    private val location =
        Location(
            uri = "file:///T.kt",
            range = Range(Position(line = 3, character = 0), Position(line = 3, character = 9)),
        )
    private val locationJson =
        """{"uri":"file:///T.kt","range":{"start":{"line":3,"character":0},"end":{"line":3,"character":9}}}"""

    private fun parse(text: String): JsonElement = wireJson.parseToJsonElement(text)

    @Test
    fun `TestStart golden JSON`() {
        wireJson.encodeToString(TestStart.serializer(), TestStart(displayName = "t")) shouldBe
            """{"displayName":"t"}"""
        wireJson.encodeToString(TestStart.serializer(), TestStart(displayName = "t", location = location)) shouldBe
            """{"displayName":"t","location":$locationJson}"""
    }

    @Test
    fun `TestFinish golden JSON`() {
        wireJson.encodeToString(
            TestFinish.serializer(),
            TestFinish(displayName = "t", status = TestStatus.Failed),
        ) shouldBe """{"displayName":"t","status":2}"""

        val full =
            TestFinish(
                displayName = "t",
                message = "boom",
                status = TestStatus.Skipped,
                location = location,
                dataKind = "vendor-trace",
                data = buildJsonObject { put("trace", "x") },
            )
        val expected =
            """{"displayName":"t","message":"boom","status":5,"location":$locationJson,""" +
                """"dataKind":"vendor-trace","data":{"trace":"x"}}"""
        wireJson.encodeToString(TestFinish.serializer(), full) shouldBe expected
        wireJson.decodeFromString(TestFinish.serializer(), expected) shouldBe full
    }

    @Test
    fun `TestStatus uses integer values and rejects unknown ones`() {
        TestStatus.entries.map { it.value } shouldBe listOf(1, 2, 3, 4, 5)
        for (status in TestStatus.entries) {
            val encoded = wireJson.encodeToString(TestStatus.serializer(), status)
            encoded shouldBe status.value.toString()
            wireJson.decodeFromString(TestStatus.serializer(), encoded) shouldBe status
        }
        shouldThrow<SerializationException> {
            wireJson.decodeFromString(TestStatus.serializer(), "9")
        }
    }

    @Test
    fun `TaskStartParams with test-start decodes typed`() {
        val kind = TaskStartDataKind.testStart
        val params =
            TaskStartParams(
                taskId = TaskId(id = "t1"),
                dataKind = kind.kind,
                data = kind.encode(TestStart(displayName = "a test", location = location)),
            )
        val encoded = wireJson.encodeToString(TaskStartParams.serializer(), params)
        encoded shouldBe
            """{"taskId":{"id":"t1"},"dataKind":"test-start",""" +
            """"data":{"displayName":"a test","location":$locationJson}}"""
        val decoded = wireJson.decodeFromString(TaskStartParams.serializer(), encoded)
        decoded.decodeData(kind) shouldBe TestStart(displayName = "a test", location = location)
    }

    @Test
    fun `TaskFinishParams with test-finish decodes typed`() {
        val kind = TaskFinishDataKind.testFinish
        val finish = TestFinish(displayName = "a test", status = TestStatus.Passed)
        val params =
            TaskFinishParams(
                taskId = TaskId(id = "t1"),
                status = StatusCode.Ok,
                dataKind = kind.kind,
                data = kind.encode(finish),
            )
        val encoded = wireJson.encodeToString(TaskFinishParams.serializer(), params)
        encoded shouldBe
            """{"taskId":{"id":"t1"},"status":1,"dataKind":"test-finish","data":{"displayName":"a test","status":1}}"""
        wireJson.decodeFromString(TaskFinishParams.serializer(), encoded).decodeData(kind) shouldBe finish
    }

    @Test
    fun `compile and test task kinds decode typed`() {
        val start =
            TaskStartParams(
                taskId = TaskId(id = "c"),
                dataKind = TaskStartDataKind.compileTask.kind,
                data = TaskStartDataKind.compileTask.encode(CompileTask(target)),
            )
        start.decodeData(TaskStartDataKind.compileTask) shouldBe CompileTask(target)
        start.decodeData(TaskStartDataKind.testTask) shouldBe null

        val report = CompileReport(target = target, errors = 1, warnings = 2)
        val finish =
            TaskFinishParams(
                taskId = TaskId(id = "c"),
                status = StatusCode.Error,
                dataKind = TaskFinishDataKind.compileReport.kind,
                data = TaskFinishDataKind.compileReport.encode(report),
            )
        finish.decodeData(TaskFinishDataKind.compileReport) shouldBe report

        val testReport = TestReport(target = target, passed = 1, failed = 0, ignored = 0, cancelled = 0, skipped = 0)
        TaskFinishParams(
            taskId = TaskId(id = "t"),
            status = StatusCode.Ok,
            dataKind = TaskFinishDataKind.testReport.kind,
            data = TaskFinishDataKind.testReport.encode(testReport),
        ).decodeData(TaskFinishDataKind.testReport) shouldBe testReport
        TaskStartDataKind.entries.map { it.kind } shouldBe listOf("compile-task", "test-start", "test-task")
        TaskFinishDataKind.entries.map { it.kind } shouldBe listOf("compile-report", "test-finish", "test-report")
    }

    @Test
    fun `wrong kind and absent data decode to null`() {
        val params =
            TaskStartParams(
                taskId = TaskId(id = "t"),
                dataKind = "test-start",
                data = TaskStartDataKind.testStart.encode(TestStart(displayName = "x")),
            )
        params.decodeData(TaskStartDataKind.compileTask) shouldBe null
        TaskStartParams(taskId = TaskId(id = "t"), dataKind = "test-start")
            .decodeData(TaskStartDataKind.testStart) shouldBe null
    }

    @Test
    fun `unknown kind round-trips unchanged`() {
        val input = """{"taskId":{"id":"t"},"dataKind":"vendor-x","data":{"k":[1,2],"n":null,"o":{"a":true}}}"""
        val decoded = wireJson.decodeFromString(TaskStartParams.serializer(), input)
        decoded.dataKind shouldBe "vendor-x"
        decoded.decodeData(TaskStartDataKind.compileTask) shouldBe null
        parse(wireJson.encodeToString(TaskStartParams.serializer(), decoded)) shouldBe parse(input)
    }

    @Test
    fun `known kind keeps extra payload fields in the raw data`() {
        val input =
            """{"taskId":{"id":"t"},"status":1,"dataKind":"compile-report",""" +
                """"data":{"target":{"uri":"file:///p/main"},"errors":1,"warnings":0,"extra":true}}"""
        val decoded = wireJson.decodeFromString(TaskFinishParams.serializer(), input)
        decoded.decodeData(TaskFinishDataKind.compileReport) shouldBe
            CompileReport(target = target, errors = 1, warnings = 0)
        (decoded.data as JsonObject)["extra"] shouldBe JsonPrimitive(true)
        parse(wireJson.encodeToString(TaskFinishParams.serializer(), decoded)) shouldBe parse(input)
    }

    @Test
    fun `malformed payload of a matching kind throws SerializationException`() {
        val params =
            TaskFinishParams(
                taskId = TaskId(id = "t"),
                status = StatusCode.Ok,
                dataKind = "compile-report",
                data = buildJsonObject { put("errors", 1) },
            )
        shouldThrow<SerializationException> {
            params.decodeData(TaskFinishDataKind.compileReport)
        }
    }

    @Test
    fun `SourceItem encodes required members and optional data`() {
        wireJson.encodeToString(
            SourceItem.serializer(),
            SourceItem(uri = "file:///a/", kind = SourceItemKind.Directory, generated = false),
        ) shouldBe """{"uri":"file:///a/","kind":2,"generated":false}"""

        val withData =
            SourceItem(
                uri = "file:///A.kt",
                kind = SourceItemKind.File,
                generated = true,
                dataKind = "jvm",
                data = buildJsonObject { put("packageName", "a.b") },
            )
        val expected =
            """{"uri":"file:///A.kt","kind":1,"generated":true,"dataKind":"jvm","data":{"packageName":"a.b"}}"""
        wireJson.encodeToString(SourceItem.serializer(), withData) shouldBe expected
        wireJson.decodeFromString(SourceItem.serializer(), expected) shouldBe withData
    }

    @Test
    fun `BuildTargetEvent carries dataKind`() {
        val event =
            BuildTargetEvent(
                target = target,
                kind = BuildTargetEventKind.Changed,
                dataKind = "vendor-x",
                data = JsonPrimitive(1),
            )
        val expected = """{"target":{"uri":"file:///p/main"},"kind":2,"dataKind":"vendor-x","data":1}"""
        wireJson.encodeToString(BuildTargetEvent.serializer(), event) shouldBe expected
        wireJson.decodeFromString(BuildTargetEvent.serializer(), expected) shouldBe event
    }

    @Test
    fun `InitializeBuildResult carries dataKind`() {
        val result =
            InitializeBuildResult(
                displayName = "s",
                version = "1",
                bspVersion = "2.2.0",
                capabilities = BuildServerCapabilities(),
                dataKind = "vendor-x",
                data = JsonObject(emptyMap()),
            )
        val expected =
            """{"displayName":"s","version":"1","bspVersion":"2.2.0","capabilities":{},""" +
                """"dataKind":"vendor-x","data":{}}"""
        wireJson.encodeToString(InitializeBuildResult.serializer(), result) shouldBe expected
        wireJson.decodeFromString(InitializeBuildResult.serializer(), expected) shouldBe result
    }

    @Test
    fun `bspJson omits defaults and ignores unknown keys like the connection codec`() {
        bspJson.encodeToString(TaskId.serializer(), TaskId(id = "a")) shouldBe """{"id":"a"}"""
        bspJson.decodeFromString(TaskId.serializer(), """{"id":"a","zzz":1}""") shouldBe TaskId(id = "a")
    }
}
