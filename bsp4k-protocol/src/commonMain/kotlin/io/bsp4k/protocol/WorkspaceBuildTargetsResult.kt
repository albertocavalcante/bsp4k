package io.bsp4k.protocol

import kotlinx.serialization.Serializable

/** Result of the `workspace/buildTargets` request. */
@Serializable
public data class WorkspaceBuildTargetsResult(
    val targets: List<BuildTarget>,
)
