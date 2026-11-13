package io.bsp4k.protocol

import kotlinx.serialization.Serializable

/** Parameters for the `buildTarget/destinations` request. */
@Serializable
public data class DestinationsParams(
    val target: BuildTargetIdentifier,
)

/** Result of the `buildTarget/destinations` request. */
@Serializable
public data class DestinationsResult(
    val destinations: List<Destination>,
)

/** A destination on which a build target can be compiled, tested, run or debugged. */
@Serializable
public data class Destination(
    val id: DestinationIdentifier,
    val displayName: String,
)

/** A unique identifier for a destination. */
@Serializable
public data class DestinationIdentifier(
    val uri: URI,
)
