package io.bsp4k.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

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
internal object StatusCodeSerializer : KSerializer<StatusCode> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("StatusCode", PrimitiveKind.INT)

    override fun serialize(
        encoder: Encoder,
        value: StatusCode,
    ) {
        encoder.encodeInt(value.value)
    }

    override fun deserialize(decoder: Decoder): StatusCode {
        val intValue = decoder.decodeInt()
        return StatusCode.entries.first { it.value == intValue }
    }
}
