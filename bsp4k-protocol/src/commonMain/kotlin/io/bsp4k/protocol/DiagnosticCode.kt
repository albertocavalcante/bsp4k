package io.bsp4k.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull

/** A diagnostic code: a string or an integer. */
@Serializable(with = DiagnosticCodeSerializer::class)
public sealed interface DiagnosticCode {
    /** String form. */
    public data class StringCode(
        public val value: String,
    ) : DiagnosticCode

    /** Integer form. */
    public data class IntCode(
        public val value: Int,
    ) : DiagnosticCode
}

/** Serializes [DiagnosticCode] as a JSON string or integer, discriminated by the JSON content. */
internal object DiagnosticCodeSerializer : KSerializer<DiagnosticCode> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("io.bsp4k.protocol.DiagnosticCode")

    override fun serialize(
        encoder: Encoder,
        value: DiagnosticCode,
    ) {
        val json = encoder as? JsonEncoder ?: throw SerializationException("DiagnosticCode supports JSON only")
        when (value) {
            is DiagnosticCode.StringCode -> json.encodeJsonElement(JsonPrimitive(value.value))
            is DiagnosticCode.IntCode -> json.encodeJsonElement(JsonPrimitive(value.value))
        }
    }

    override fun deserialize(decoder: Decoder): DiagnosticCode {
        val json = decoder as? JsonDecoder ?: throw SerializationException("DiagnosticCode supports JSON only")
        val element =
            json.decodeJsonElement() as? JsonPrimitive
                ?: throw SerializationException("DiagnosticCode must be a string or an integer")
        return when {
            element.isString -> DiagnosticCode.StringCode(element.content)
            element.intOrNull != null -> DiagnosticCode.IntCode(element.int)
            else -> throw SerializationException("DiagnosticCode must be a string or an integer")
        }
    }
}
