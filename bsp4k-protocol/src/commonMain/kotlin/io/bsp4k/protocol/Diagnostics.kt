package io.bsp4k.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** A position in a text document (zero-based line and character). */
@Serializable
public data class Position(
    val line: Int,
    val character: Int,
)

/** A range in a text document. */
@Serializable
public data class Range(
    val start: Position,
    val end: Position,
)

/** A location in a text document. */
@Serializable
public data class Location(
    val uri: URI,
    val range: Range,
)

/** Severity of a diagnostic. */
@Serializable(with = DiagnosticSeveritySerializer::class)
public enum class DiagnosticSeverity(
    public val value: Int,
) {
    Error(1),
    Warning(2),
    Information(3),
    Hint(4),
}

/** Serializes [DiagnosticSeverity] as its integer value. */
internal object DiagnosticSeveritySerializer : IntEnumSerializer<DiagnosticSeverity>(
    "DiagnosticSeverity",
    DiagnosticSeverity.entries,
    DiagnosticSeverity::value,
)

/** A diagnostic message (compiler error, warning, etc.). */
@Serializable
public data class Diagnostic(
    val range: Range,
    val severity: DiagnosticSeverity? = null,
    val code: String? = null,
    val source: String? = null,
    val message: String,
    val relatedInformation: List<DiagnosticRelatedInformation>? = null,
    val dataKind: String? = null,
    val data: JsonElement? = null,
)

/** Related information for a diagnostic. */
@Serializable
public data class DiagnosticRelatedInformation(
    val location: Location,
    val message: String,
)

/** Parameters for the `build/publishDiagnostics` notification. */
@Serializable
public data class PublishDiagnosticsParams(
    val textDocument: TextDocumentIdentifier,
    val buildTarget: BuildTargetIdentifier,
    val originId: String? = null,
    val diagnostics: List<Diagnostic>,
    val reset: Boolean = false,
)
