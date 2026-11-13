package io.bsp4k.protocol

import kotlinx.serialization.Serializable

/** sbt-specific build target metadata (`dataKind` is [SbtDataKinds.BUILD_TARGET]). */
@Serializable
public data class SbtBuildTarget(
    val sbtVersion: String,
    val autoImports: List<String>,
    val scalaBuildTarget: ScalaBuildTarget,
    val parent: BuildTargetIdentifier? = null,
    val children: List<BuildTargetIdentifier>,
)

/** `dataKind` strings defined by the sbt extension. */
public object SbtDataKinds {
    /** `BuildTarget.dataKind` for [SbtBuildTarget]. */
    public const val BUILD_TARGET: String = "sbt"
}
