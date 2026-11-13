package io.bsp4k.client

import io.jsonrpc4k.core.JsonRpcCodec
import io.jsonrpc4k.core.JsonRpcMessageDecoder
import io.jsonrpc4k.core.NotificationMessage
import io.jsonrpc4k.core.RequestMessage
import io.jsonrpc4k.core.ResponseError
import io.jsonrpc4k.core.ResponseMessage
import io.jsonrpc4k.transport.Transport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.assertEquals

internal const val INIT_OK: String =
    """{"displayName":"srv","version":"1.0","bspVersion":"2.2.0","capabilities":{}}"""

internal const val TARGET: String = """{"uri":"t"}"""

private fun parse(text: String): JsonElement? = if (text == "null") null else Json.parseToJsonElement(text)

/**
 * In-memory server peer. Records every frame the client sends and answers requests from
 * [results] (JSON text, "null" for a null result), [errors], or not at all for [silent] methods.
 */
internal class FakePeer(
    initializeResult: String = INIT_OK,
    results: Map<String, String> = emptyMap(),
    private val errors: Map<String, ResponseError> = emptyMap(),
    private val silent: Set<String> = emptySet(),
) : Transport {
    private val results = results + ("build/initialize" to initializeResult)
    private val inbound = Channel<ByteArray>(Channel.UNLIMITED)
    val requests = mutableListOf<RequestMessage>()
    val notifications = mutableListOf<NotificationMessage>()
    override val isConnected: Boolean = true
    override val incoming: Flow<ByteArray> = inbound.receiveAsFlow()

    override suspend fun send(data: ByteArray) {
        for (message in JsonRpcMessageDecoder().feed(data)) {
            when (message) {
                is RequestMessage -> answer(message)
                is NotificationMessage -> notifications += message
                else -> Unit
            }
        }
    }

    private suspend fun answer(request: RequestMessage) {
        requests += request
        if (request.method in silent) return
        val response =
            errors[request.method]?.let { ResponseMessage.error(request.id, it) }
                ?: ResponseMessage.success(request.id, results[request.method]?.let(::parse))
        inbound.send(JsonRpcCodec.Default.encode(response))
    }

    /** Delivers a server-to-client notification to the client. */
    suspend fun push(
        method: String,
        paramsJson: String?,
    ) {
        inbound.send(JsonRpcCodec.Default.encode(NotificationMessage(method = method, params = paramsJson?.let(::parse))))
    }

    override suspend fun close() {
        inbound.close()
    }
}

/** Starts a session on [peer] and completes the initialize handshake. */
internal suspend fun TestScope.connect(
    peer: FakePeer,
    config: BuildClientConfig = buildClient {},
): BuildClientSession {
    val session = config.start(peer, backgroundScope)
    session.initialize()
    return session
}

/** Performs [call] against a fresh session and asserts the exact method and params it put on the wire. */
internal suspend fun TestScope.assertCall(
    method: String,
    paramsJson: String?,
    resultJson: String,
    call: suspend BuildServerProxy.() -> Unit,
) {
    val peer = FakePeer(results = mapOf(method to resultJson))
    val session = connect(peer)
    session.server.call()
    val sent = peer.requests.last()
    assertEquals(method, sent.method)
    assertEquals(paramsJson?.let(::parse), sent.params)
}

/** Performs [call] against a fresh session and asserts the notification it put on the wire. */
internal suspend fun TestScope.assertNotify(
    method: String,
    paramsJson: String,
    call: suspend BuildServerProxy.() -> Unit,
) {
    val peer = FakePeer()
    val session = connect(peer)
    session.server.call()
    session.shutdown()
    val sent = peer.notifications.single { it.method == method }
    assertEquals(parse(paramsJson), sent.params)
}
