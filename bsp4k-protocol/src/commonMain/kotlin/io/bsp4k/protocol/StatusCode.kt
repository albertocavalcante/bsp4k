package io.bsp4k.protocol

import kotlinx.serialization.Serializable

/** Status code for BSP compile, test, and run results. */
@Serializable(with = StatusCodeSerializer::class)
public enum class StatusCode(
    public val value: Int,
) {
    Ok(1),
    Error(2),
    Cancelled(3),
}

/** Serializes [StatusCode] as its integer value. */
internal object StatusCodeSerializer : IntEnumSerializer<StatusCode>(
    "StatusCode",
    StatusCode.entries,
    StatusCode::value,
)
