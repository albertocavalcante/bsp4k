package io.bsp4k.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Parameters for the `buildTarget/compile` request. */
@Serializable
public data class CompileParams(
    val targets: List<BuildTargetIdentifier>,
    val originId: String? = null,
    val arguments: List<String>? = null,
)

/** Result of the `buildTarget/compile` request. */
@Serializable
public data class CompileResult(
    val originId: String? = null,
    val statusCode: StatusCode,
    val dataKind: String? = null,
    val data: JsonElement? = null,
)
