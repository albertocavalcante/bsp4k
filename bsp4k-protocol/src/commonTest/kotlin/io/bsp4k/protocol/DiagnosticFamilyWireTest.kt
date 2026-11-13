package io.bsp4k.protocol

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test

class DiagnosticFamilyWireTest {
    private val range = Range(Position(line = 1, character = 0), Position(line = 1, character = 4))
    private val rangeJson = """{"start":{"line":1,"character":0},"end":{"line":1,"character":4}}"""
    private val strict = Json

    @Test
    fun `Diagnostic encodes required members only when others are absent`() {
        val encoded = wireJson.encodeToString(Diagnostic.serializer(), Diagnostic(range = range, message = "m"))
        encoded shouldBe """{"range":$rangeJson,"message":"m"}"""
    }

    @Test
    fun `Diagnostic with integer code encodes a JSON number`() {
        val diagnostic =
            Diagnostic(
                range = range,
                severity = DiagnosticSeverity.Warning,
                code = DiagnosticCode.IntCode(123),
                codeDescription = CodeDescription(href = "https://example.com/E123"),
                source = "lint",
                message = "m",
                tags = listOf(DiagnosticTag.UNNECESSARY, DiagnosticTag.DEPRECATED),
                dataKind = "scala",
                data = buildJsonObject { put("actions", 0) },
            )
        val expected =
            """{"range":$rangeJson,"severity":2,"code":123,"codeDescription":{"href":"https://example.com/E123"},""" +
                """"source":"lint","message":"m","tags":[1,2],"dataKind":"scala","data":{"actions":0}}"""
        wireJson.encodeToString(Diagnostic.serializer(), diagnostic) shouldBe expected
        wireJson.decodeFromString(Diagnostic.serializer(), expected) shouldBe diagnostic
        strict.decodeFromString(Diagnostic.serializer(), expected) shouldBe diagnostic
    }

    @Test
    fun `Diagnostic with string code encodes a JSON string`() {
        val diagnostic = Diagnostic(range = range, code = DiagnosticCode.StringCode("123"), message = "m")
        val expected = """{"range":$rangeJson,"code":"123","message":"m"}"""
        wireJson.encodeToString(Diagnostic.serializer(), diagnostic) shouldBe expected
        wireJson.decodeFromString(Diagnostic.serializer(), expected) shouldBe diagnostic
        strict.decodeFromString(Diagnostic.serializer(), expected) shouldBe diagnostic
    }

    @Test
    fun `integer code keeps its JSON type under both codecs`() {
        val input = """{"range":$rangeJson,"code":7,"message":"m"}"""
        for (codec in listOf(wireJson, strict)) {
            val decoded = codec.decodeFromString(Diagnostic.serializer(), input)
            decoded.code shouldBe DiagnosticCode.IntCode(7)
            codec.encodeToString(Diagnostic.serializer(), decoded) shouldBe input
        }
    }

    @Test
    fun `DiagnosticCode rejects values that are neither string nor int`() {
        for (bad in listOf("true", "1.5", "null", "[1]", "{}", "3000000000")) {
            shouldThrow<SerializationException> {
                wireJson.decodeFromString(DiagnosticCode.serializer(), bad)
            }
        }
    }

    @Test
    fun `unknown diagnostic tags are preserved`() {
        val input = """{"range":$rangeJson,"message":"m","tags":[1,7]}"""
        val decoded = wireJson.decodeFromString(Diagnostic.serializer(), input)
        decoded.tags shouldBe listOf(1, 7)
        wireJson.encodeToString(Diagnostic.serializer(), decoded) shouldBe input
    }

    @Test
    fun `CodeDescription requires href`() {
        wireJson.encodeToString(CodeDescription.serializer(), CodeDescription(href = "https://x")) shouldBe
            """{"href":"https://x"}"""
        shouldThrow<SerializationException> {
            wireJson.decodeFromString(CodeDescription.serializer(), "{}")
        }
    }

    @Test
    fun `DiagnosticTag and BuildTargetTag constants match BSP 2_2_0`() {
        DiagnosticTag.entries shouldBe listOf(1, 2)
        BuildTargetTag.entries shouldBe
            listOf("library", "application", "test", "integration-test", "benchmark", "no-ide", "manual")
    }

    @Test
    fun `BuildTarget keeps unknown tags`() {
        val target =
            BuildTarget(
                id = BuildTargetIdentifier(uri = "file:///p"),
                tags = listOf(BuildTargetTag.LIBRARY, "vendor-tag"),
                capabilities = BuildTargetCapabilities(),
                languageIds = emptyList(),
                dependencies = emptyList(),
            )
        val encoded = wireJson.encodeToString(BuildTarget.serializer(), target)
        wireJson.decodeFromString(BuildTarget.serializer(), encoded).tags shouldBe listOf("library", "vendor-tag")
    }
}
