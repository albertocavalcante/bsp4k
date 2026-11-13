package io.bsp4k.protocol

import kotlinx.serialization.Serializable

/** Describes how to start a BSP server and the capabilities it supports (`.bsp/*.json`). */
@Serializable
public data class BspConnectionDetails(
    val name: String,
    val argv: List<String>,
    val version: String,
    val bspVersion: String,
    val languages: List<String>,
)
