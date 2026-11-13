package io.bsp4k.protocol

import kotlinx.serialization.json.Json

/**
 * Production-equivalent JSON configuration, mirroring `io.jsonrpc4k.core.defaultJson`
 * (`jsonrpc4k-core/.../Codec.kt`). Tests that assert wire output must use this instead of
 * `encodeDefaults = true`, which hides required members dropped by the real codec.
 */
internal val wireJson: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        isLenient = true
        coerceInputValues = true
    }
