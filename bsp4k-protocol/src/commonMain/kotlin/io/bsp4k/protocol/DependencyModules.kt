package io.bsp4k.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Parameters for the `buildTarget/dependencyModules` request. */
@Serializable
public data class DependencyModulesParams(
    val targets: List<BuildTargetIdentifier>,
)

/** Result of the `buildTarget/dependencyModules` request. */
@Serializable
public data class DependencyModulesResult(
    val items: List<DependencyModulesItem>,
)

/** Dependency modules for a single build target. */
@Serializable
public data class DependencyModulesItem(
    val target: BuildTargetIdentifier,
    val modules: List<DependencyModule>,
)

/** A single dependency module. */
@Serializable
public data class DependencyModule(
    val name: String,
    val version: String,
    override val dataKind: String? = null,
    override val data: JsonElement? = null,
) : BspDataCarrier
