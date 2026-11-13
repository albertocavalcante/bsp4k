package io.bsp4k.protocol

import kotlinx.serialization.Serializable

/** Parameters of `run/printStdout` and `run/printStderr`. Unstable in BSP 2.2. */
@Serializable
public data class PrintParams(
    val originId: String,
    val task: TaskId? = null,
    val message: String,
)

/** Parameters of `run/readStdin`. Unstable in BSP 2.2. */
@Serializable
public data class ReadParams(
    val originId: String,
    val task: TaskId? = null,
    val message: String,
)
