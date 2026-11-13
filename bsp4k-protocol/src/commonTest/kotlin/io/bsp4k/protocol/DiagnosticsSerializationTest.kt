package io.bsp4k.protocol

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import kotlin.test.Test

class DiagnosticsSerializationTest {
    private val json = wireJson
    private val jsonNoDefaults = Json

    @Test
    fun `Position roundtrips`() {
        val original = Position(line = 10, character = 5)
        val encoded = json.encodeToString(Position.serializer(), original)
        val decoded = json.decodeFromString(Position.serializer(), encoded)
        decoded shouldBe original
    }

    @Test
    fun `Range roundtrips`() {
        val original =
            Range(
                start = Position(line = 0, character = 0),
                end = Position(line = 0, character = 10),
            )
        val encoded = json.encodeToString(Range.serializer(), original)
        val decoded = json.decodeFromString(Range.serializer(), encoded)
        decoded shouldBe original
    }

    @Test
    fun `Location roundtrips`() {
        val original =
            Location(
                uri = "file:///src/Main.kt",
                range =
                    Range(
                        start = Position(line = 5, character = 0),
                        end = Position(line = 5, character = 20),
                    ),
            )
        val encoded = json.encodeToString(Location.serializer(), original)
        val decoded = json.decodeFromString(Location.serializer(), encoded)
        decoded shouldBe original
    }

    @Test
    fun `Diagnostic minimal roundtrips`() {
        val original =
            Diagnostic(
                range =
                    Range(
                        start = Position(line = 1, character = 0),
                        end = Position(line = 1, character = 10),
                    ),
                message = "Unresolved reference",
            )
        val encoded = jsonNoDefaults.encodeToString(Diagnostic.serializer(), original)
        val decoded = jsonNoDefaults.decodeFromString(Diagnostic.serializer(), encoded)
        decoded shouldBe original
    }

    @Test
    fun `Diagnostic full roundtrips`() {
        val original =
            Diagnostic(
                range =
                    Range(
                        start = Position(line = 5, character = 0),
                        end = Position(line = 5, character = 15),
                    ),
                severity = DiagnosticSeverity.Error,
                code = "KT001",
                source = "kotlin",
                message = "Type mismatch",
                relatedInformation =
                    listOf(
                        DiagnosticRelatedInformation(
                            location =
                                Location(
                                    uri = "file:///src/Other.kt",
                                    range =
                                        Range(
                                            start = Position(line = 10, character = 4),
                                            end = Position(line = 10, character = 20),
                                        ),
                                ),
                            message = "Expected type declared here",
                        ),
                    ),
            )
        val encoded = json.encodeToString(Diagnostic.serializer(), original)
        val decoded = json.decodeFromString(Diagnostic.serializer(), encoded)
        decoded shouldBe original
    }

    @Test
    fun `PublishDiagnosticsParams roundtrips`() {
        val original =
            PublishDiagnosticsParams(
                textDocument = TextDocumentIdentifier(uri = "file:///src/Main.kt"),
                buildTarget = BuildTargetIdentifier(uri = "file:///project/main"),
                originId = "compile-1",
                diagnostics =
                    listOf(
                        Diagnostic(
                            range =
                                Range(
                                    start = Position(line = 0, character = 0),
                                    end = Position(line = 0, character = 5),
                                ),
                            severity = DiagnosticSeverity.Warning,
                            message = "Unused import",
                        ),
                    ),
                reset = true,
            )
        val encoded = json.encodeToString(PublishDiagnosticsParams.serializer(), original)
        val decoded = json.decodeFromString(PublishDiagnosticsParams.serializer(), encoded)
        decoded shouldBe original
    }

    @Test
    fun `PublishDiagnosticsParams with empty diagnostics roundtrips`() {
        val original =
            PublishDiagnosticsParams(
                textDocument = TextDocumentIdentifier(uri = "file:///src/Main.kt"),
                buildTarget = BuildTargetIdentifier(uri = "file:///project/main"),
                diagnostics = emptyList(),
                reset = false,
            )
        val encoded = json.encodeToString(PublishDiagnosticsParams.serializer(), original)
        encoded shouldContain "\"reset\":false"
        val decoded = json.decodeFromString(PublishDiagnosticsParams.serializer(), encoded)
        decoded shouldBe original
    }
}
