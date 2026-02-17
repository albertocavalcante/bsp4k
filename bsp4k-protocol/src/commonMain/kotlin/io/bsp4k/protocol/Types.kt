package io.bsp4k.protocol

import kotlinx.serialization.Serializable

/** A URI represented as a string. */
public typealias URI = String

/** A unique identifier for a build target. */
@Serializable
public data class BuildTargetIdentifier(
    val uri: URI,
)
