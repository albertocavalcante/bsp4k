package io.bsp4k.protocol

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerializationException
import kotlin.test.Test

class RunIoSerializationTest {
    @Test
    fun `PrintParams encodes required originId and message and omits task`() {
        val params = PrintParams(originId = "run-1", message = "hello\n")
        wireJson.encodeToString(PrintParams.serializer(), params) shouldBe """{"originId":"run-1","message":"hello\n"}"""
    }

    @Test
    fun `PrintParams encodes task and roundtrips`() {
        val params = PrintParams(originId = "run-1", task = TaskId(id = "t1"), message = "out")
        val text = """{"originId":"run-1","task":{"id":"t1"},"message":"out"}"""
        wireJson.encodeToString(PrintParams.serializer(), params) shouldBe text
        wireJson.decodeFromString(PrintParams.serializer(), text) shouldBe params
    }

    @Test
    fun `PrintParams without originId fails to decode`() {
        shouldThrow<SerializationException> {
            wireJson.decodeFromString(PrintParams.serializer(), """{"message":"x"}""")
        }
    }

    @Test
    fun `ReadParams encodes required originId and message and omits task`() {
        val params = ReadParams(originId = "run-2", message = "input\n")
        wireJson.encodeToString(ReadParams.serializer(), params) shouldBe """{"originId":"run-2","message":"input\n"}"""
    }

    @Test
    fun `ReadParams encodes task and roundtrips`() {
        val params = ReadParams(originId = "run-2", task = TaskId(id = "t2", parents = listOf("t1")), message = "in")
        val text = """{"originId":"run-2","task":{"id":"t2","parents":["t1"]},"message":"in"}"""
        wireJson.encodeToString(ReadParams.serializer(), params) shouldBe text
        wireJson.decodeFromString(ReadParams.serializer(), text) shouldBe params
    }

    @Test
    fun `run stdio method constants match spec`() {
        BspMethods.RUN_PRINT_STDOUT shouldBe "run/printStdout"
        BspMethods.RUN_PRINT_STDERR shouldBe "run/printStderr"
        BspMethods.RUN_READ_STDIN shouldBe "run/readStdin"
    }
}
