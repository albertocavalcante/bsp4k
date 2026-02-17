package io.bsp4k.protocol

import kotlinx.serialization.Serializable

/** Parameters for the `buildTarget/resources` request. */
@Serializable
public data class ResourcesParams(
    val targets: List<BuildTargetIdentifier>,
)

/** Result of the `buildTarget/resources` request. */
@Serializable
public data class ResourcesResult(
    val items: List<ResourcesItem>,
)

/** Resources for a single build target. */
@Serializable
public data class ResourcesItem(
    val target: BuildTargetIdentifier,
    val resources: List<URI>,
)
