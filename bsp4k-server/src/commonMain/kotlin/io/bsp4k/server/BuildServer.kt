package io.bsp4k.server

import io.bsp4k.protocol.BspMethods
import io.bsp4k.protocol.InitializeBuildParams
import io.bsp4k.protocol.InitializeBuildResult
import io.jsonrpc4k.core.BoundedDispatchPolicy
import io.jsonrpc4k.core.Connection
import io.jsonrpc4k.core.InboundResourceLimits
import io.jsonrpc4k.core.JsonRpcCodec
import io.jsonrpc4k.core.JsonRpcException
import io.jsonrpc4k.core.OutboundResourceLimits
import io.jsonrpc4k.core.ResponseError
import io.jsonrpc4k.transport.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement

/**
 * The receiver of every handler and lifecycle hook: what server code can reach while it runs.
 *
 * @property client Sends server-to-client notifications.
 */
@BspServerDsl
public class BuildServerContext internal constructor(
    public val client: BuildClientProxy,
)

/**
 * A running build server instance.
 *
 * Created via [BuildServerConfig.start]. The server handles `build/initialize`, `build/initialized`,
 * `build/shutdown` and `build/exit` itself, following [BuildServerState], and calls the configured
 * [LifecycleHooks]. Application handlers run only in [BuildServerState.Running].
 *
 * Inbound `$/cancelRequest` is handled by jsonrpc4k before any handler: a bounded session answers the
 * cancelled request with RequestCancelled (-32800) and cancels its handler coroutine, while an unbounded
 * session ignores it.
 */
public class BuildServer internal constructor(
    private val config: BuildServerConfig,
    private val connection: Connection,
    private val lifecycle: SessionLifecycle,
) {
    private val lifecycleState = LifecycleState()

    /** The protocol lifecycle state; see [BuildServerState]. */
    public val state: StateFlow<BuildServerState> = lifecycleState.flow

    /** Sends notifications to the connected client. */
    public val client: BuildClientProxy = BuildClientProxy(connection)

    private val context = BuildServerContext(client)

    internal suspend fun setup() {
        connection.onRequest(BspMethods.BUILD_INITIALIZE) { params -> initialize(params) }
        connection.onNotification(BspMethods.BUILD_INITIALIZED) { _ -> initialized() }
        connection.onRequest(BspMethods.BUILD_SHUTDOWN) { _ -> shutdown() }
        connection.onNotification(BspMethods.BUILD_EXIT) { _ -> exit() }

        config.requestHandlers.forEach { (method, handler) ->
            connection.onRequest(method) { params ->
                if (lifecycleState.current != BuildServerState.Running) throw stateError(method)
                handler(context, params)
            }
        }

        // BSP drops notifications that arrive before initialization completes or after shutdown.
        config.notificationHandlers.forEach { (method, handler) ->
            connection.onNotification(method) { params ->
                if (lifecycleState.current == BuildServerState.Running) handler(context, params)
            }
        }
    }

    /** Stops immediately, canceling pending work and discarding unsent frames; the transport remains caller-owned. */
    public fun close(): Unit = lifecycle.close()

    /**
     * Waits for session jobs to stop. Returns a terminal read, write, or bounded core failure,
     * or null on EOF, explicit close, graceful exit, or parent cancellation. The session records
     * its first observed end, then closes the core; a core failure claimed before that close
     * takes precedence. Concurrent sources do not have a global first-event ordering. The transport remains caller-owned
     * and its suspending operations must cooperate with coroutine cancellation. A caller may
     * need to close its transport to unblock a blocking read before this function can return.
     */
    public suspend fun awaitTermination(): Throwable? = lifecycle.awaitTermination()

    private suspend fun initialize(params: JsonElement?): JsonElement {
        if (!lifecycleState.beginInitialize()) {
            throw JsonRpcException(ResponseError.INVALID_REQUEST, "Server already initialized or shutting down")
        }
        val result =
            try {
                initializeResult(params)
            } catch (failure: Throwable) {
                lifecycleState.abortInitialize()
                throw failure
            }
        lifecycleState.completeInitialize()
        return result
    }

    private suspend fun initializeResult(params: JsonElement?): JsonElement {
        val clientParams = decodeParams(BspMethods.BUILD_INITIALIZE, InitializeBuildParams.serializer(), params)
        val data = InitializeResultBuilder()
        config.lifecycleHooks.onInitialize?.invoke(data, clientParams)
        val result =
            InitializeBuildResult(
                displayName = config.serverName,
                version = config.serverVersion,
                bspVersion = config.bspVersion,
                capabilities = config.capabilities,
                dataKind = data.dataKind,
                data = data.data,
            )
        return JsonRpcCodec.defaultJson.encodeToJsonElement(InitializeBuildResult.serializer(), result)
    }

    private suspend fun initialized() {
        if (lifecycleState.markInitialized()) {
            config.lifecycleHooks.onInitialized?.invoke(context)
        }
    }

    private suspend fun shutdown(): JsonElement? {
        if (!lifecycleState.requestShutdown()) throw stateError(BspMethods.BUILD_SHUTDOWN)
        config.lifecycleHooks.onShutdown?.invoke(context)
        return null
    }

    private suspend fun exit() {
        val previous = lifecycleState.exit()
        if (previous == BuildServerState.Exited) return
        try {
            lifecycle.flushOutgoing()
        } finally {
            try {
                val exitCode = if (previous == BuildServerState.ShutdownRequested) 0 else 1
                config.lifecycleHooks.onExit?.invoke(context, exitCode)
            } finally {
                lifecycle.close()
            }
        }
    }

    /** The error for a request that the current state refuses. */
    private fun stateError(method: String): JsonRpcException =
        if (lifecycleState.current < BuildServerState.ShutdownRequested) {
            JsonRpcException(ResponseError.SERVER_NOT_INITIALIZED, "Server not initialized")
        } else {
            JsonRpcException(ResponseError.INVALID_REQUEST, "Server is shutting down, cannot process request: $method")
        }
}

/**
 * Starts a build server with the given [transport] and optional [scope].
 *
 * The returned [BuildServer] is fully wired: incoming transport data is
 * decoded and dispatched, and outgoing messages are forwarded to the transport.
 * The supplied scope and transport remain caller-owned; closing a session does not close the transport.
 * Requests run one at a time in arrival order, and inbound `$/cancelRequest` is ignored.
 */
public suspend fun BuildServerConfig.start(
    transport: Transport,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
): BuildServer = startSession(transport, SessionLifecycle(scope))

/**
 * Starts an explicitly bounded session using the fixed default JSON codec.
 * Limits are wire-size proxies, not a total heap bound. Typed payload conversion to JSON happens
 * before outbound admission and is outside these limits. The supplied scope and transport remain
 * caller-owned. A transport collector must not await reentrant sends on the same connection.
 * Requests may run concurrently up to [policy]; inbound `$/cancelRequest` answers the request with
 * RequestCancelled (-32800) and cancels its handler coroutine.
 */
public suspend fun BuildServerConfig.start(
    transport: Transport,
    policy: BoundedDispatchPolicy,
    inboundLimits: InboundResourceLimits,
    outboundLimits: OutboundResourceLimits,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
): BuildServer = startSession(transport, SessionLifecycle(scope) { Connection(policy, inboundLimits, outboundLimits, it) })

private suspend fun BuildServerConfig.startSession(
    transport: Transport,
    lifecycle: SessionLifecycle,
): BuildServer {
    val server = BuildServer(this, lifecycle.connection, lifecycle)
    try {
        server.setup()
        lifecycle.start(transport)
    } catch (failure: Throwable) {
        lifecycle.fail(failure)
        throw failure
    }
    return server
}
