package io.bsp4k.protocol

import kotlinx.serialization.Serializable

/** Parameters for the `buildTarget/dependencySources` request. */
@Serializable
public data class DependencySourcesParams(
    val targets: List<BuildTargetIdentifier>,
)

/** Result of the `buildTarget/dependencySources` request. */
@Serializable
public data class DependencySourcesResult(
    val items: List<DependencySourcesItem>,
)

/** Dependency sources for a single build target. */
@Serializable
public data class DependencySourcesItem(
    val target: BuildTargetIdentifier,
    val sources: List<URI>,
)
