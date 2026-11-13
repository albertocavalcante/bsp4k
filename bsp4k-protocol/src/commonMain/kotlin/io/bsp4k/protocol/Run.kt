package io.bsp4k.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Parameters for the `buildTarget/run` request. */
@Serializable
public data class RunParams(
    val target: BuildTargetIdentifier,
    val destination: DestinationIdentifier? = null,
    val originId: String? = null,
    val arguments: List<String>? = null,
    val environmentVariables: Map<String, String>? = null,
    val workingDirectory: URI? = null,
    override val dataKind: String? = null,
    override val data: JsonElement? = null,
) : BspDataCarrier

/** Result of the `buildTarget/run` request. */
@Serializable
public data class RunResult(
    val originId: String? = null,
    val statusCode: StatusCode,
)
