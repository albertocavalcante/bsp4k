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

/** Parameters for the `$/cancelRequest` notification. */
@Serializable
public data class CancelRequestParams(
    val id: RequestId,
)

/** A JSON-RPC request id as modeled by BSP: a string or a 32-bit integer. */
@Serializable(with = RequestIdSerializer::class)
public sealed interface RequestId {
    /** String form. */
    public data class StringId(
        public val value: String,
    ) : RequestId

    /** Integer form. */
    public data class IntId(
        public val value: Int,
    ) : RequestId
}

/** Serializes [RequestId] as a bare JSON string or integer. */
internal object RequestIdSerializer : KSerializer<RequestId> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("io.bsp4k.protocol.RequestId")

    override fun serialize(
        encoder: Encoder,
        value: RequestId,
    ) {
        val json = encoder as? JsonEncoder ?: throw SerializationException("RequestId supports JSON only")
        when (value) {
            is RequestId.StringId -> json.encodeJsonElement(JsonPrimitive(value.value))
            is RequestId.IntId -> json.encodeJsonElement(JsonPrimitive(value.value))
        }
    }

    override fun deserialize(decoder: Decoder): RequestId {
        val json = decoder as? JsonDecoder ?: throw SerializationException("RequestId supports JSON only")
        val element =
            json.decodeJsonElement() as? JsonPrimitive
                ?: throw SerializationException("RequestId must be a string or an integer")
        return when {
            element.isString -> RequestId.StringId(element.content)
            element.intOrNull != null -> RequestId.IntId(element.int)
            else -> throw SerializationException("RequestId must be a string or an integer")
        }
    }
}
