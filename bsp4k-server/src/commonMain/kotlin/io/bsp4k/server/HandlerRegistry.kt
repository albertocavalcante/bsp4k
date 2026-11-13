package io.bsp4k.server

import io.jsonrpc4k.core.JsonRpcCodec
import io.jsonrpc4k.core.JsonRpcException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer

/** Collects the handlers of one [BuildServerBuilder]; each method may be registered once. */
internal class HandlerRegistry {
    val requests: MutableMap<String, ServerRequestHandler> = mutableMapOf()
    val notifications: MutableMap<String, ServerNotificationHandler> = mutableMapOf()

    fun registerRequest(
        method: String,
        handler: ServerRequestHandler,
    ) {
        require(method !in requests) { "Handler already registered for method: $method" }
        requests[method] = handler
    }

    fun registerNotification(
        method: String,
        handler: ServerNotificationHandler,
    ) {
        require(method !in notifications) { "Handler already registered for method: $method" }
        notifications[method] = handler
    }

    /** Registers a request whose params decode as [params] and whose result encodes as [result]. */
    fun <P, R> request(
        method: String,
        params: KSerializer<P>,
        result: KSerializer<R>,
        handler: suspend BuildServerContext.(P) -> R,
    ) {
        registerRequest(method) { raw ->
            JsonRpcCodec.defaultJson.encodeToJsonElement(result, handler(this, decodeParams(method, params, raw)))
        }
    }

    /** Registers a request without params whose result encodes as [result]; sent params are ignored. */
    fun <R> paramlessRequest(
        method: String,
        result: KSerializer<R>,
        handler: suspend BuildServerContext.() -> R,
    ) {
        registerRequest(method) { JsonRpcCodec.defaultJson.encodeToJsonElement(result, handler(this)) }
    }

    /** Registers a notification whose params decode as [params]; undecodable params are dropped. */
    fun <P> notification(
        method: String,
        params: KSerializer<P>,
        handler: suspend BuildServerContext.(P) -> Unit,
    ) {
        registerNotification(method) { raw -> handler(this, decodeParams(method, params, raw)) }
    }
}

internal inline fun <reified P, reified R> HandlerRegistry.handleRequest(
    method: String,
    noinline handler: suspend BuildServerContext.(P) -> R,
) {
    request(method, serializer<P>(), serializer<R>(), handler)
}

internal inline fun <reified R> HandlerRegistry.handleParamless(
    method: String,
    noinline handler: suspend BuildServerContext.() -> R,
) {
    paramlessRequest(method, serializer<R>(), handler)
}

internal inline fun <reified P> HandlerRegistry.handleNotification(
    method: String,
    noinline handler: suspend BuildServerContext.(P) -> Unit,
) {
    notification(method, serializer<P>(), handler)
}

/**
 * Decodes required params, mapping absent or malformed params to InvalidParams (-32602). The error omits
 * decoder details, as JSON-RPC handler failures do.
 */
internal fun <P> decodeParams(
    method: String,
    serializer: KSerializer<P>,
    params: JsonElement?,
): P {
    if (params == null) throw JsonRpcException.invalidParams("Missing params for $method")
    return try {
        JsonRpcCodec.defaultJson.decodeFromJsonElement(serializer, params)
    } catch (invalid: IllegalArgumentException) {
        throw JsonRpcException.invalidParams("Invalid params for $method")
    }
}
