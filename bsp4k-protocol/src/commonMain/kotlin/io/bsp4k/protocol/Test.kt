package io.bsp4k.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Parameters for the `buildTarget/test` request. */
@Serializable
public data class TestParams(
    val targets: List<BuildTargetIdentifier>,
    val originId: String? = null,
    val arguments: List<String>? = null,
    override val dataKind: String? = null,
    override val data: JsonElement? = null,
) : BspDataCarrier

/** Result of the `buildTarget/test` request. */
@Serializable
public data class TestResult(
    val originId: String? = null,
    val statusCode: StatusCode,
    override val dataKind: String? = null,
    override val data: JsonElement? = null,
) : BspDataCarrier
