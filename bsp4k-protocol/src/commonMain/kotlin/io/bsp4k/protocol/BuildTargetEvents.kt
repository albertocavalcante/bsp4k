package io.bsp4k.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Parameters for the `buildTarget/didChange` notification. */
@Serializable
public data class DidChangeBuildTarget(
    val changes: List<BuildTargetEvent>,
)

/** An event describing a change to a build target. */
@Serializable
public data class BuildTargetEvent(
    val target: BuildTargetIdentifier,
    val kind: BuildTargetEventKind? = null,
    val data: JsonElement? = null,
)

/** The kind of change to a build target. */
@Serializable(with = BuildTargetEventKindSerializer::class)
public enum class BuildTargetEventKind(
    public val value: Int,
) {
    Created(1),
    Changed(2),
    Deleted(3),
}

/** Serializes [BuildTargetEventKind] as its integer value. */
internal object BuildTargetEventKindSerializer : IntEnumSerializer<BuildTargetEventKind>(
    "BuildTargetEventKind",
    BuildTargetEventKind.entries,
    BuildTargetEventKind::value,
)
