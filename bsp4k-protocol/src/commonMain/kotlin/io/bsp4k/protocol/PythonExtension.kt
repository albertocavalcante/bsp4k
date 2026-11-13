package io.bsp4k.protocol

import kotlinx.serialization.Serializable

/** Python-specific build target data; carried in [BuildTarget.data] with `dataKind` [KIND]. */
@Serializable
public data class PythonBuildTarget(
    val version: String? = null,
    val interpreter: URI? = null,
) {
    public companion object {
        /** The `dataKind` of build target data of this shape. */
        public const val KIND: String = "python"
    }
}

/** Parameters of `buildTarget/pythonOptions`. */
@Serializable
public data class PythonOptionsParams(
    val targets: List<BuildTargetIdentifier>,
)

/** Result of `buildTarget/pythonOptions`. */
@Serializable
public data class PythonOptionsResult(
    val items: List<PythonOptionsItem>,
)

/** Python interpreter options for one build target. */
@Serializable
public data class PythonOptionsItem(
    val target: BuildTargetIdentifier,
    val interpreterOptions: List<String>,
)
