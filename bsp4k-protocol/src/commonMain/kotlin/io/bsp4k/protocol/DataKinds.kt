package io.bsp4k.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** A protocol type carrying a polymorphic `data` payload discriminated by `dataKind`. */
public interface BspDataCarrier {
    /** Discriminator naming the payload shape of [data]; unknown kinds are allowed. */
    public val dataKind: String?

    /** Raw payload, preserved verbatim whatever [dataKind] says. */
    public val data: JsonElement?
}

/** A known `dataKind` for carrier [H] and the serializer of its payload [T]. */
public class BspDataKind<H : BspDataCarrier, T>(
    public val kind: String,
    public val serializer: KSerializer<T>,
) {
    /** Encodes [value] as a `data` payload. */
    public fun encode(
        value: T,
        json: Json = bspJson,
    ): JsonElement = json.encodeToJsonElement(serializer, value)

    override fun toString(): String = "BspDataKind($kind)"
}

/**
 * Decodes `data` as [kind] when `dataKind` equals its kind; returns null for another kind or absent data.
 * The raw fields are never modified, so unknown kinds re-encode unchanged. Throws a
 * `SerializationException` when the kind matches but the payload is malformed.
 */
public fun <H : BspDataCarrier, T> H.decodeData(
    kind: BspDataKind<H, T>,
    json: Json = bspJson,
): T? = if (dataKind == kind.kind) data?.let { json.decodeFromJsonElement(kind.serializer, it) } else null

/** Known kinds for [TaskStartParams.data]. */
public object TaskStartDataKind {
    /** `compile-task`: [CompileTask]. */
    public val compileTask: BspDataKind<TaskStartParams, CompileTask> =
        BspDataKind("compile-task", CompileTask.serializer())

    /** `test-start`: [TestStart]. */
    public val testStart: BspDataKind<TaskStartParams, TestStart> =
        BspDataKind("test-start", TestStart.serializer())

    /** `test-task`: [TestTask]. */
    public val testTask: BspDataKind<TaskStartParams, TestTask> =
        BspDataKind("test-task", TestTask.serializer())

    /** All kinds defined by BSP 2.2.0 for this carrier. */
    public val entries: List<BspDataKind<TaskStartParams, *>> = listOf(compileTask, testStart, testTask)
}

/** Known kinds for [TaskFinishParams.data]. */
public object TaskFinishDataKind {
    /** `compile-report`: [CompileReport]. */
    public val compileReport: BspDataKind<TaskFinishParams, CompileReport> =
        BspDataKind("compile-report", CompileReport.serializer())

    /** `test-finish`: [TestFinish]. */
    public val testFinish: BspDataKind<TaskFinishParams, TestFinish> =
        BspDataKind("test-finish", TestFinish.serializer())

    /** `test-report`: [TestReport]. */
    public val testReport: BspDataKind<TaskFinishParams, TestReport> =
        BspDataKind("test-report", TestReport.serializer())

    /** All kinds defined by BSP 2.2.0 for this carrier. */
    public val entries: List<BspDataKind<TaskFinishParams, *>> = listOf(compileReport, testFinish, testReport)
}
