package io.bsp4k.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** A build target with its metadata. */
@Serializable
public data class BuildTarget(
    val id: BuildTargetIdentifier,
    val displayName: String? = null,
    val baseDirectory: URI? = null,
    val tags: List<String> = emptyList(),
    val capabilities: BuildTargetCapabilities,
    val languageIds: List<String> = emptyList(),
    val dependencies: List<BuildTargetIdentifier> = emptyList(),
    val dataKind: String? = null,
    val data: JsonElement? = null,
)

/** Capabilities of a build target. */
@Serializable
public data class BuildTargetCapabilities(
    val canCompile: Boolean = false,
    val canTest: Boolean = false,
    val canRun: Boolean = false,
    val canDebug: Boolean = false,
)
