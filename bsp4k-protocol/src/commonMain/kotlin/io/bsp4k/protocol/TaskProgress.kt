package io.bsp4k.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Unique identifier for a task. */
@Serializable
public data class TaskId(
    val id: String,
    val parents: List<String>? = null,
)

/** Parameters for the `build/taskStart` notification. */
@Serializable
public data class TaskStartParams(
    val taskId: TaskId,
    val originId: String? = null,
    val eventTime: Long? = null,
    val message: String? = null,
    override val dataKind: String? = null,
    override val data: JsonElement? = null,
) : BspDataCarrier

/** Parameters for the `build/taskProgress` notification. */
@Serializable
public data class TaskProgressParams(
    val taskId: TaskId,
    val originId: String? = null,
    val eventTime: Long? = null,
    val message: String? = null,
    val total: Long? = null,
    val progress: Long? = null,
    val unit: String? = null,
    override val dataKind: String? = null,
    override val data: JsonElement? = null,
) : BspDataCarrier

/** Parameters for the `build/taskFinish` notification. */
@Serializable
public data class TaskFinishParams(
    val taskId: TaskId,
    val originId: String? = null,
    val eventTime: Long? = null,
    val message: String? = null,
    val status: StatusCode,
    override val dataKind: String? = null,
    override val data: JsonElement? = null,
) : BspDataCarrier

/** Data for a compile task start event. */
@Serializable
public data class CompileTask(
    val target: BuildTargetIdentifier,
)

/** Data for a compile task finish event. */
@Serializable
public data class CompileReport(
    val target: BuildTargetIdentifier,
    val originId: String? = null,
    val errors: Int,
    val warnings: Int,
    val time: Long? = null,
    val noOp: Boolean? = null,
)

/** Data for a test task start event. */
@Serializable
public data class TestTask(
    val target: BuildTargetIdentifier,
)

/** Data for a test task finish event. */
@Serializable
public data class TestReport(
    val target: BuildTargetIdentifier,
    val originId: String? = null,
    val passed: Int,
    val failed: Int,
    val ignored: Int,
    val cancelled: Int,
    val skipped: Int,
    val time: Long? = null,
)

/** Data for a test start event: a single test began. */
@Serializable
public data class TestStart(
    val displayName: String,
    val location: Location? = null,
)

/** Data for a test finish event: a single test completed. */
@Serializable
public data class TestFinish(
    val displayName: String,
    val message: String? = null,
    val status: TestStatus,
    val location: Location? = null,
    override val dataKind: String? = null,
    override val data: JsonElement? = null,
) : BspDataCarrier

/** Completion status of a single test. */
@Serializable(with = TestStatusSerializer::class)
public enum class TestStatus(
    public val value: Int,
) {
    Passed(1),
    Failed(2),
    Ignored(3),
    Cancelled(4),
    Skipped(5),
}

/** Serializes [TestStatus] as its integer value. */
internal object TestStatusSerializer : IntEnumSerializer<TestStatus>(
    "TestStatus",
    TestStatus.entries,
    TestStatus::value,
)
