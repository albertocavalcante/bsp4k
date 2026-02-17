package io.bsp4k.protocol

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test

class IntEnumSerializationTest {
    private val json = Json

    // ==================== StatusCode ====================

    @Test
    fun `StatusCode Ok roundtrips`() {
        val encoded = json.encodeToString(StatusCode.serializer(), StatusCode.Ok)
        json.decodeFromString(StatusCode.serializer(), encoded) shouldBe StatusCode.Ok
    }

    @Test
    fun `StatusCode Error roundtrips`() {
        val encoded = json.encodeToString(StatusCode.serializer(), StatusCode.Error)
        json.decodeFromString(StatusCode.serializer(), encoded) shouldBe StatusCode.Error
    }

    @Test
    fun `StatusCode Cancelled roundtrips`() {
        val encoded = json.encodeToString(StatusCode.serializer(), StatusCode.Cancelled)
        json.decodeFromString(StatusCode.serializer(), encoded) shouldBe StatusCode.Cancelled
    }

    @Test
    fun `StatusCode encodes to correct int values`() {
        json.encodeToJsonElement(StatusCode.serializer(), StatusCode.Ok) shouldBe JsonPrimitive(1)
        json.encodeToJsonElement(StatusCode.serializer(), StatusCode.Error) shouldBe JsonPrimitive(2)
        json.encodeToJsonElement(StatusCode.serializer(), StatusCode.Cancelled) shouldBe JsonPrimitive(3)
    }

    // ==================== MessageType ====================

    @Test
    fun `MessageType all values roundtrip`() {
        MessageType.entries.forEach { entry ->
            val encoded = json.encodeToString(MessageType.serializer(), entry)
            json.decodeFromString(MessageType.serializer(), encoded) shouldBe entry
        }
    }

    @Test
    fun `MessageType encodes to correct int values`() {
        json.encodeToJsonElement(MessageType.serializer(), MessageType.Error) shouldBe JsonPrimitive(1)
        json.encodeToJsonElement(MessageType.serializer(), MessageType.Warning) shouldBe JsonPrimitive(2)
        json.encodeToJsonElement(MessageType.serializer(), MessageType.Info) shouldBe JsonPrimitive(3)
        json.encodeToJsonElement(MessageType.serializer(), MessageType.Log) shouldBe JsonPrimitive(4)
    }

    // ==================== DiagnosticSeverity ====================

    @Test
    fun `DiagnosticSeverity all values roundtrip`() {
        DiagnosticSeverity.entries.forEach { entry ->
            val encoded = json.encodeToString(DiagnosticSeverity.serializer(), entry)
            json.decodeFromString(DiagnosticSeverity.serializer(), encoded) shouldBe entry
        }
    }

    @Test
    fun `DiagnosticSeverity encodes to correct int values`() {
        json.encodeToJsonElement(DiagnosticSeverity.serializer(), DiagnosticSeverity.Error) shouldBe JsonPrimitive(1)
        json.encodeToJsonElement(DiagnosticSeverity.serializer(), DiagnosticSeverity.Warning) shouldBe JsonPrimitive(2)
        json.encodeToJsonElement(DiagnosticSeverity.serializer(), DiagnosticSeverity.Information) shouldBe JsonPrimitive(3)
        json.encodeToJsonElement(DiagnosticSeverity.serializer(), DiagnosticSeverity.Hint) shouldBe JsonPrimitive(4)
    }

    // ==================== SourceItemKind ====================

    @Test
    fun `SourceItemKind all values roundtrip`() {
        SourceItemKind.entries.forEach { entry ->
            val encoded = json.encodeToString(SourceItemKind.serializer(), entry)
            json.decodeFromString(SourceItemKind.serializer(), encoded) shouldBe entry
        }
    }

    @Test
    fun `SourceItemKind encodes to correct int values`() {
        json.encodeToJsonElement(SourceItemKind.serializer(), SourceItemKind.File) shouldBe JsonPrimitive(1)
        json.encodeToJsonElement(SourceItemKind.serializer(), SourceItemKind.Directory) shouldBe JsonPrimitive(2)
    }

    // ==================== OutputPathItemKind ====================

    @Test
    fun `OutputPathItemKind all values roundtrip`() {
        OutputPathItemKind.entries.forEach { entry ->
            val encoded = json.encodeToString(OutputPathItemKind.serializer(), entry)
            json.decodeFromString(OutputPathItemKind.serializer(), encoded) shouldBe entry
        }
    }

    @Test
    fun `OutputPathItemKind encodes to correct int values`() {
        json.encodeToJsonElement(OutputPathItemKind.serializer(), OutputPathItemKind.File) shouldBe JsonPrimitive(1)
        json.encodeToJsonElement(OutputPathItemKind.serializer(), OutputPathItemKind.Directory) shouldBe JsonPrimitive(2)
    }

    // ==================== BuildTargetEventKind ====================

    @Test
    fun `BuildTargetEventKind all values roundtrip`() {
        BuildTargetEventKind.entries.forEach { entry ->
            val encoded = json.encodeToString(BuildTargetEventKind.serializer(), entry)
            json.decodeFromString(BuildTargetEventKind.serializer(), encoded) shouldBe entry
        }
    }

    @Test
    fun `BuildTargetEventKind encodes to correct int values`() {
        json.encodeToJsonElement(BuildTargetEventKind.serializer(), BuildTargetEventKind.Created) shouldBe JsonPrimitive(1)
        json.encodeToJsonElement(BuildTargetEventKind.serializer(), BuildTargetEventKind.Changed) shouldBe JsonPrimitive(2)
        json.encodeToJsonElement(BuildTargetEventKind.serializer(), BuildTargetEventKind.Deleted) shouldBe JsonPrimitive(3)
    }
}
