package io.bsp4k.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Parameters for the `debugSession/start` request. */
@Serializable
public data class DebugSessionParams(
    val targets: List<BuildTargetIdentifier>,
    val dataKind: String? = null,
    val data: JsonElement? = null,
)

/** Result of the `debugSession/start` request. */
@Serializable
public data class DebugSessionAddress(
    val uri: URI,
)
