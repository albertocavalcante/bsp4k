package io.bsp4k.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Parameters for the `build/initialize` request. */
@Serializable
public data class InitializeBuildParams(
    val displayName: String,
    val version: String,
    val bspVersion: String,
    val rootUri: URI,
    val capabilities: BuildClientCapabilities,
    override val dataKind: String? = null,
    override val data: JsonElement? = null,
) : BspDataCarrier

/** Result of the `build/initialize` request. */
@Serializable
public data class InitializeBuildResult(
    val displayName: String,
    val version: String,
    val bspVersion: String,
    val capabilities: BuildServerCapabilities,
    override val dataKind: String? = null,
    override val data: JsonElement? = null,
) : BspDataCarrier
