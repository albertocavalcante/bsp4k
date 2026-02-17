package io.bsp4k.protocol

import kotlinx.serialization.Serializable

/** Parameters for the `buildTarget/outputPaths` request. */
@Serializable
public data class OutputPathsParams(
    val targets: List<BuildTargetIdentifier>,
)

/** Result of the `buildTarget/outputPaths` request. */
@Serializable
public data class OutputPathsResult(
    val items: List<OutputPathsItem>,
)

/** Output paths for a single build target. */
@Serializable
public data class OutputPathsItem(
    val target: BuildTargetIdentifier,
    val outputPaths: List<OutputPathItem>,
)

/** A single output path. */
@Serializable
public data class OutputPathItem(
    val uri: URI,
    val kind: OutputPathItemKind,
)

/** Whether an output path is a file or directory. */
@Serializable(with = OutputPathItemKindSerializer::class)
public enum class OutputPathItemKind(
    public val value: Int,
) {
    File(1),
    Directory(2),
}

/** Serializes [OutputPathItemKind] as its integer value. */
internal object OutputPathItemKindSerializer : IntEnumSerializer<OutputPathItemKind>(
    "OutputPathItemKind",
    OutputPathItemKind.entries,
    OutputPathItemKind::value,
)
