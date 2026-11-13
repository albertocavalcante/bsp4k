package io.bsp4k.protocol

import kotlinx.serialization.Serializable

/** C++-specific build target data; carried in [BuildTarget.data] with `dataKind` [KIND]. */
@Serializable
public data class CppBuildTarget(
    val version: String? = null,
    val compiler: String? = null,
    val cCompiler: URI? = null,
    val cppCompiler: URI? = null,
) {
    public companion object {
        /** The `dataKind` of build target data of this shape. */
        public const val KIND: String = "cpp"
    }
}

/** Parameters of `buildTarget/cppOptions`. */
@Serializable
public data class CppOptionsParams(
    val targets: List<BuildTargetIdentifier>,
)

/** Result of `buildTarget/cppOptions`. */
@Serializable
public data class CppOptionsResult(
    val items: List<CppOptionsItem>,
)

/** C++ compiler and linker options for one build target. */
@Serializable
public data class CppOptionsItem(
    val target: BuildTargetIdentifier,
    val copts: List<String>,
    val defines: List<String>,
    val linkopts: List<String>,
    val linkshared: Boolean? = null,
)
