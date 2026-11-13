package io.bsp4k.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Parameters for the `buildTarget/sources` request. */
@Serializable
public data class SourcesParams(
    val targets: List<BuildTargetIdentifier>,
)

/** Result of the `buildTarget/sources` request. */
@Serializable
public data class SourcesResult(
    val items: List<SourcesItem>,
)

/** Sources for a single build target. */
@Serializable
public data class SourcesItem(
    val target: BuildTargetIdentifier,
    val sources: List<SourceItem>,
    val roots: List<URI> = emptyList(),
)

/** A single source file or directory. */
@Serializable
public data class SourceItem(
    val uri: URI,
    val kind: SourceItemKind,
    val generated: Boolean,
    override val dataKind: String? = null,
    override val data: JsonElement? = null,
) : BspDataCarrier

/** Whether a source item is a file or directory. */
@Serializable(with = SourceItemKindSerializer::class)
public enum class SourceItemKind(
    public val value: Int,
) {
    File(1),
    Directory(2),
}

/** Serializes [SourceItemKind] as its integer value. */
internal object SourceItemKindSerializer : IntEnumSerializer<SourceItemKind>(
    "SourceItemKind",
    SourceItemKind.entries,
    SourceItemKind::value,
)
