package io.bsp4k.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Parameters for the `buildTarget/run` request. */
@Serializable
public data class RunParams(
    val target: BuildTargetIdentifier,
    val originId: String? = null,
    val arguments: List<String>? = null,
    val dataKind: String? = null,
    val data: JsonElement? = null,
)

/** Result of the `buildTarget/run` request. */
@Serializable
public data class RunResult(
    val originId: String? = null,
    val statusCode: StatusCode,
)
