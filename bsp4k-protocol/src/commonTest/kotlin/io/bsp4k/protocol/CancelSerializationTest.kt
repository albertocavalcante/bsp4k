package io.bsp4k.protocol

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test

class CancelSerializationTest {
    private val strictJson = Json

    @Test
    fun `cancel method constant matches spec`() {
        BspMethods.CANCEL_REQUEST shouldBe "$/cancelRequest"
    }

    @Test
    fun `string request id encodes as JSON string`() {
        val params = CancelRequestParams(id = RequestId.StringId("abc"))
        wireJson.encodeToString(CancelRequestParams.serializer(), params) shouldBe """{"id":"abc"}"""
    }

    @Test
    fun `integer request id encodes as JSON number`() {
        val params = CancelRequestParams(id = RequestId.IntId(42))
        wireJson.encodeToString(CancelRequestParams.serializer(), params) shouldBe """{"id":42}"""
    }

    @Test
    fun `request ids decode with their JSON types under both codecs`() {
        for (codec in listOf(wireJson, strictJson)) {
            codec.decodeFromString(CancelRequestParams.serializer(), """{"id":"7"}""").id shouldBe RequestId.StringId("7")
            codec.decodeFromString(CancelRequestParams.serializer(), """{"id":7}""").id shouldBe RequestId.IntId(7)
        }
    }

    @Test
    fun `non string non integer request ids are rejected`() {
        for (bad in listOf("1.5", "true", "null", "[]", "{}")) {
            shouldThrow<SerializationException> {
                strictJson.decodeFromString(CancelRequestParams.serializer(), """{"id":$bad}""")
            }
        }
    }

    @Test
    fun `missing id is rejected`() {
        shouldThrow<SerializationException> {
            wireJson.decodeFromString(CancelRequestParams.serializer(), "{}")
        }
    }
}
