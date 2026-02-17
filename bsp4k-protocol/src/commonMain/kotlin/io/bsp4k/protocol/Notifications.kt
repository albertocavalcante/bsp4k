package io.bsp4k.protocol

import kotlinx.serialization.Serializable

/** Message type for log and show message notifications. */
@Serializable(with = MessageTypeSerializer::class)
public enum class MessageType(
    public val value: Int,
) {
    Error(1),
    Warning(2),
    Info(3),
    Log(4),
}

/** Serializes [MessageType] as its integer value. */
internal object MessageTypeSerializer : IntEnumSerializer<MessageType>(
    "MessageType",
    MessageType.entries,
    MessageType::value,
)

/** Parameters for the `build/logMessage` notification. */
@Serializable
public data class LogMessageParams(
    val type: MessageType,
    val task: TaskId? = null,
    val originId: String? = null,
    val message: String,
)

/** Parameters for the `build/showMessage` notification. */
@Serializable
public data class ShowMessageParams(
    val type: MessageType,
    val task: TaskId? = null,
    val originId: String? = null,
    val message: String,
)
