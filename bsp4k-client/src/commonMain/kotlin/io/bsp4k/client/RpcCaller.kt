package io.bsp4k.client

import io.jsonrpc4k.core.Connection
import io.jsonrpc4k.core.JsonRpcCodec
import io.jsonrpc4k.core.JsonRpcException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.serializer
import kotlin.time.Duration

/** Sends gated requests and notifications over a [Connection] using the default codec JSON. */
internal class RpcCaller(
    private val connection: Connection,
    private val gate: SessionGate,
) {
    val json = JsonRpcCodec.defaultJson

    suspend fun requestUngated(
        method: String,
        params: JsonElement?,
        timeout: Duration,
    ): JsonElement? = connection.request<JsonElement>(method, params, timeout)

    suspend fun <R> call(
        method: String,
        params: JsonElement?,
        resultSerializer: KSerializer<R>,
        timeout: Duration,
    ): R {
        gate.requireReady(method)
        val raw = requestUngated(method, params, timeout)
        if (raw == null || raw is JsonNull) throw JsonRpcException.internalError("$method returned a null result")
        return json.decodeFromJsonElement(resultSerializer, raw)
    }

    suspend fun callUnit(
        method: String,
        params: JsonElement?,
        timeout: Duration,
    ) {
        gate.requireReady(method)
        requestUngated(method, params, timeout)
    }

    suspend fun notifyGated(
        method: String,
        params: JsonElement?,
    ) {
        gate.requireReady(method)
        connection.notify(method, params)
    }
}

internal suspend inline fun <reified P, reified R> RpcCaller.request(
    method: String,
    params: P,
    timeout: Duration,
): R = call(method, json.encodeToJsonElement(serializer<P>(), params), serializer<R>(), timeout)

internal suspend inline fun <reified R> RpcCaller.requestNoParams(
    method: String,
    timeout: Duration,
): R = call(method, null, serializer<R>(), timeout)

internal suspend inline fun <reified P> RpcCaller.notification(
    method: String,
    params: P,
) = notifyGated(method, json.encodeToJsonElement(serializer<P>(), params))
