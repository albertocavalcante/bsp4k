package io.bsp4k.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Reusable serializer for enum classes that map to integer values in JSON.
 *
 * @param serialName The serial name for the descriptor.
 * @param entries All enum entries.
 * @param toValue Extracts the integer value from an enum entry.
 */
internal open class IntEnumSerializer<T>(
    serialName: String,
    private val entries: List<T>,
    private val toValue: (T) -> Int,
) : KSerializer<T> where T : Enum<T> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(serialName, PrimitiveKind.INT)

    override fun serialize(
        encoder: Encoder,
        value: T,
    ) {
        encoder.encodeInt(toValue(value))
    }

    override fun deserialize(decoder: Decoder): T {
        val intValue = decoder.decodeInt()
        return entries.firstOrNull { toValue(it) == intValue }
            ?: throw SerializationException("Unknown ${descriptor.serialName} value: $intValue")
    }
}
