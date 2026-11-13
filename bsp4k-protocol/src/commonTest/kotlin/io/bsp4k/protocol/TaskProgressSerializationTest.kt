package io.bsp4k.protocol

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlin.test.Test

class TaskProgressSerializationTest {
    private val json = wireJson
    private val jsonNoDefaults = Json

    @Test
    fun `TaskId roundtrips without parents`() {
        val original = TaskId(id = "task-1")
        val encoded = jsonNoDefaults.encodeToString(TaskId.serializer(), original)
        val decoded = jsonNoDefaults.decodeFromString(TaskId.serializer(), encoded)
        decoded shouldBe original
    }

    @Test
    fun `TaskId roundtrips with parents`() {
        val original = TaskId(id = "task-2", parents = listOf("task-1"))
        val encoded = json.encodeToString(TaskId.serializer(), original)
        val decoded = json.decodeFromString(TaskId.serializer(), encoded)
        decoded shouldBe original
    }

    @Test
    fun `TaskStartParams roundtrips`() {
        val original =
            TaskStartParams(
                taskId = TaskId(id = "compile-1"),
                message = "Compiling project",
                dataKind = "compile-task",
            )
        val encoded = jsonNoDefaults.encodeToString(TaskStartParams.serializer(), original)
        val decoded = jsonNoDefaults.decodeFromString(TaskStartParams.serializer(), encoded)
        decoded shouldBe original
    }

    @Test
    fun `TaskProgressParams roundtrips`() {
        val original =
            TaskProgressParams(
                taskId = TaskId(id = "compile-1"),
                message = "Compiling 5/10 files",
                total = 10,
                progress = 5,
                unit = "files",
            )
        val encoded = jsonNoDefaults.encodeToString(TaskProgressParams.serializer(), original)
        val decoded = jsonNoDefaults.decodeFromString(TaskProgressParams.serializer(), encoded)
        decoded shouldBe original
    }

    @Test
    fun `TaskFinishParams roundtrips`() {
        val original =
            TaskFinishParams(
                taskId = TaskId(id = "compile-1"),
                message = "Compilation finished",
                status = StatusCode.Ok,
            )
        val encoded = jsonNoDefaults.encodeToString(TaskFinishParams.serializer(), original)
        val decoded = jsonNoDefaults.decodeFromString(TaskFinishParams.serializer(), encoded)
        decoded shouldBe original
    }

    @Test
    fun `TaskFinishParams with error status roundtrips`() {
        val original =
            TaskFinishParams(
                taskId = TaskId(id = "compile-2"),
                status = StatusCode.Error,
                message = "Compilation failed",
            )
        val encoded = jsonNoDefaults.encodeToString(TaskFinishParams.serializer(), original)
        val decoded = jsonNoDefaults.decodeFromString(TaskFinishParams.serializer(), encoded)
        decoded shouldBe original
    }

    @Test
    fun `CompileTask roundtrips`() {
        val original =
            CompileTask(
                target = BuildTargetIdentifier(uri = "file:///project/main"),
            )
        val encoded = json.encodeToString(CompileTask.serializer(), original)
        val decoded = json.decodeFromString(CompileTask.serializer(), encoded)
        decoded shouldBe original
    }

    @Test
    fun `CompileReport roundtrips`() {
        val original =
            CompileReport(
                target = BuildTargetIdentifier(uri = "file:///project/main"),
                originId = "compile-1",
                errors = 2,
                warnings = 5,
                time = 1500,
                noOp = false,
            )
        val encoded = json.encodeToString(CompileReport.serializer(), original)
        val decoded = json.decodeFromString(CompileReport.serializer(), encoded)
        decoded shouldBe original
    }

    @Test
    fun `TestTask roundtrips`() {
        val original =
            TestTask(
                target = BuildTargetIdentifier(uri = "file:///project/test"),
            )
        val encoded = json.encodeToString(TestTask.serializer(), original)
        val decoded = json.decodeFromString(TestTask.serializer(), encoded)
        decoded shouldBe original
    }

    @Test
    fun `TestReport roundtrips`() {
        val original =
            TestReport(
                target = BuildTargetIdentifier(uri = "file:///project/test"),
                originId = "test-1",
                passed = 10,
                failed = 2,
                ignored = 1,
                cancelled = 0,
                skipped = 3,
                time = 5000,
            )
        val encoded = json.encodeToString(TestReport.serializer(), original)
        val decoded = json.decodeFromString(TestReport.serializer(), encoded)
        decoded shouldBe original
    }
}
