package io.bsp4k.protocol

import kotlinx.serialization.Serializable

/** Maven-like metadata for a dependency module (`dataKind` is [MavenDataKinds.DEPENDENCY_MODULE]). */
@Serializable
public data class MavenDependencyModule(
    val organization: String,
    val name: String,
    val version: String,
    val artifacts: List<MavenDependencyModuleArtifact>,
    val scope: String? = null,
)

/** An artifact of a Maven dependency module; [classifier] is empty or `sources`/`docs`. */
@Serializable
public data class MavenDependencyModuleArtifact(
    val uri: URI,
    val classifier: String? = null,
)

/** `dataKind` strings defined by the Maven extension. */
public object MavenDataKinds {
    /** `DependencyModule.dataKind` for [MavenDependencyModule]. */
    public const val DEPENDENCY_MODULE: String = "maven"
}
