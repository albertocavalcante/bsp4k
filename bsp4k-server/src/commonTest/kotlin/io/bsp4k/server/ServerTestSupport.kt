package io.bsp4k.server

import io.bsp4k.protocol.BSP_VERSION
import io.bsp4k.protocol.BspMethods
import io.bsp4k.protocol.BuildClientCapabilities
import io.bsp4k.protocol.InitializeBuildParams
import io.jsonrpc4k.core.JsonRpcCodec
import io.jsonrpc4k.core.JsonRpcMessageDecoder
import io.jsonrpc4k.core.Message
import io.jsonrpc4k.core.NotificationMessage
import io.jsonrpc4k.core.Payload
import io.jsonrpc4k.core.RequestId
import io.jsonrpc4k.core.RequestMessage
import io.jsonrpc4k.core.ResponseMessage
import io.jsonrpc4k.transport.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** In-memory transport shared by the server tests: the test writes client frames and reads server frames. */
internal class MemoryTransport : Transport {
    val toServer = Channel<ByteArray>(Channel.UNLIMITED)
    val fromServer = Channel<ByteArray>(Channel.UNLIMITED)
    override val incoming: Flow<ByteArray> = toServer.receiveAsFlow()
    override val isConnected: Boolean = true
    var closed = false

    override suspend fun send(data: ByteArray) {
        fromServer.send(data)
    }

    override suspend fun close() {
        closed = true
    }
}

/** Plays the BSP client over a [MemoryTransport]; responses and notifications are matched as they arrive. */
internal class TestClient(
    private val transport: MemoryTransport,
) {
    private val decoder = JsonRpcMessageDecoder()
    private val received = ArrayDeque<Message>()
    private var nextId = 0L

    /** Sends a request and waits for its response, keeping notifications that arrive first. */
    suspend fun request(
        method: String,
        params: JsonElement? = null,
    ): ResponseMessage = response(sendRequest(method, params))

    /** Sends a request without waiting; pair it with [response]. */
    suspend fun sendRequest(
        method: String,
        params: JsonElement? = null,
    ): RequestId {
        nextId += 1
        val id = RequestId.of(nextId)
        transport.toServer.send(JsonRpcCodec.Default.encode(RequestMessage(id = id, method = method, params = params)))
        return id
    }

    /** Waits for the response to request [id]. */
    suspend fun response(id: RequestId): ResponseMessage = awaitMessage { it is ResponseMessage && it.id == id } as ResponseMessage

    /** Sends a notification. A later [request] is processed after it. */
    suspend fun notify(
        method: String,
        params: JsonElement? = null,
    ) {
        transport.toServer.send(JsonRpcCodec.Default.encode(NotificationMessage(method = method, params = params)))
    }

    /** Waits for the next server notification. */
    suspend fun notification(): NotificationMessage = awaitMessage { it is NotificationMessage } as NotificationMessage

    /** Server notifications received but not yet consumed. */
    fun pendingNotifications(): List<NotificationMessage> = received.filterIsInstance<NotificationMessage>()

    private suspend fun awaitMessage(predicate: (Message) -> Boolean): Message {
        while (true) {
            val index = received.indexOfFirst(predicate)
            if (index >= 0) return received.removeAt(index)
            val frame = withTimeout(5_000) { transport.fromServer.receive() }
            received.addAll(decoder.feedMessages(frame))
        }
    }
}

/** Starts this config over a fresh [MemoryTransport] in [scope] and returns the server with its test client. */
internal suspend fun BuildServerConfig.connect(scope: CoroutineScope): Pair<BuildServer, TestClient> {
    val transport = MemoryTransport()
    return start(transport, scope) to TestClient(transport)
}

internal val testJson = JsonRpcCodec.defaultJson

internal fun <T> encode(
    serializer: KSerializer<T>,
    value: T,
): JsonElement = testJson.encodeToJsonElement(serializer, value)

internal val clientInitializeParams =
    InitializeBuildParams(
        displayName = "test-client",
        version = "1.0",
        bspVersion = BSP_VERSION,
        rootUri = "file:///workspace",
        capabilities = BuildClientCapabilities(languageIds = listOf("kotlin")),
    )

/** Sends `build/initialize` with [params]. */
internal suspend fun TestClient.initialize(
    params: JsonElement? = encode(InitializeBuildParams.serializer(), clientInitializeParams),
): ResponseMessage = request(BspMethods.BUILD_INITIALIZE, params)

/** Completes the handshake: a successful `build/initialize`, then `build/initialized`. */
internal suspend fun TestClient.handshake(): ResponseMessage {
    val response = initialize()
    assertNull(response.error, "initialize failed")
    notify(BspMethods.BUILD_INITIALIZED)
    return response
}

/** Asserts that [response] is a JSON-RPC error with [code]. */
internal fun assertError(
    code: Int,
    response: ResponseMessage,
) {
    assertEquals(code, assertNotNull(response.error, "expected error $code, got ${response.result}").code)
}

/** Asserts that [response] succeeded with a null result. */
internal fun assertNullResult(response: ResponseMessage) {
    assertNull(response.error, "unexpected error")
    assertTrue(response.result == null || response.result == JsonNull, "expected a null result, got ${response.result}")
}

/** Feeds [data] and returns the decoded messages, flattening batches; invalid payloads fail the test. */
internal fun JsonRpcMessageDecoder.feedMessages(data: ByteArray): List<Message> =
    feed(data).flatMap { payload ->
        when (payload) {
            is Payload.Single -> listOf(payload.message)
            is Payload.Batch -> payload.entries.filterNotNull()
            is Payload.Invalid -> error("Unexpected invalid payload: ${payload.error}")
        }
    }
