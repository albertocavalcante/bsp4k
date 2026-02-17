package io.bsp4k.protocol

import kotlinx.serialization.Serializable

/** Parameters for the `buildTarget/cleanCache` request. */
@Serializable
public data class CleanCacheParams(
    val targets: List<BuildTargetIdentifier>,
)

/** Result of the `buildTarget/cleanCache` request. */
@Serializable
public data class CleanCacheResult(
    val message: String? = null,
    val cleaned: Boolean,
)
