package io.bsp4k.protocol

import kotlinx.serialization.json.Json

/**
 * JSON configuration for typed views over `data` payloads. It mirrors the flags of the connection codec
 * (`io.jsonrpc4k.core.defaultJson`) so typed decoding and encoding agree with what peers see on the wire.
 */
public val bspJson: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        isLenient = true
        coerceInputValues = true
    }
