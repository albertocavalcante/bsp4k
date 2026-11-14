package io.bsp4k.client

import io.bsp4k.protocol.BspMethods
import io.bsp4k.protocol.InitializeBuildParams
import io.bsp4k.protocol.InitializeBuildResult
import io.jsonrpc4k.core.Connection
import io.jsonrpc4k.core.JsonRpcCodec
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlin.time.Duration

private val semver = Regex("""(\d+)\.\d+\.\d+(?:[-+].*)?""")

private fun invalid(
    message: String,
    cause: Throwable? = null,
): Nothing = throw InitializeResultException(message, cause)

/**
 * A running build client session.
 *
 * Requests are only allowed in [SessionState.READY], which [initialize] enters after the server's
 * result validates. Calls before that, or after [shutdown] or [close], throw [SessionStateException].
 */
public class BuildClientSession internal constructor(
    private val config: BuildClientConfig,
    private val connection: Connection,
    private val lifecycle: SessionLifecycle,
) {
    private val json = JsonRpcCodec.defaultJson
    private val gate = SessionGate()

    /** The server's `build/initialize` result, or null until [initialize] succeeds. */
    public var initializeResult: InitializeBuildResult? = null
        private set

    /** Current lifecycle state. */
    public val state: SessionState get() = gate.state

    /** Server proxy for sending requests and notifications to the build server. */
    public val server: BuildServerProxy = BuildServerProxy(RpcCaller(connection, gate))

    internal suspend fun setup() {
        config.notificationHandlers.forEach { (method, handler) ->
            connection.onNotification(method, handler)
        }
    }

    internal fun assumeInitialized(): BuildClientSession = apply { gate.assumeReady() }

    /**
     * Sends `build/initialize`, validates the server's result and only then sends `build/initialized`.
     * Returns the server's [InitializeBuildResult] (capabilities and data). A missing, undecodable or
     * invalid result throws [InitializeResultException] and leaves the session [SessionState.FAILED].
     */
    public suspend fun initialize(timeout: Duration = defaultRequestTimeout): InitializeBuildResult {
        gate.beginInitialize()
        try {
            val result = validated(requestInitialize(timeout))
            connection.notify(BspMethods.BUILD_INITIALIZED, null)
            initializeResult = result
            gate.completeInitialize()
            return result
        } catch (failure: Throwable) {
            gate.failInitialize()
            throw failure
        }
    }

    private suspend fun requestInitialize(timeout: Duration): InitializeBuildResult {
        val params =
            InitializeBuildParams(
                displayName = config.displayName,
                version = config.version,
                bspVersion = config.bspVersion,
                rootUri = config.rootUri,
                capabilities = config.capabilities,
            )
        val raw: JsonElement? =
            RpcCaller(connection, gate).requestUngated(
                BspMethods.BUILD_INITIALIZE,
                json.encodeToJsonElement(InitializeBuildParams.serializer(), params),
                timeout,
            )
        if (raw == null || raw is JsonNull) invalid("build/initialize returned a null result")
        return try {
            json.decodeFromJsonElement(InitializeBuildResult.serializer(), raw)
        } catch (failure: SerializationException) {
            invalid("build/initialize result is malformed: ${failure.message}", failure)
        }
    }

    private fun validated(result: InitializeBuildResult): InitializeBuildResult {
        if (result.displayName.isBlank()) invalid("build/initialize result has a blank displayName")
        if (result.version.isBlank()) invalid("build/initialize result has a blank version")
        val serverMajor =
            semver.matchEntire(result.bspVersion)?.groupValues?.get(1)
                ?: invalid("build/initialize result has invalid bspVersion: '${result.bspVersion}'")
        val clientMajor = semver.matchEntire(config.bspVersion)?.groupValues?.get(1)
        if (clientMajor != null && clientMajor != serverMajor) {
            invalid("Server bspVersion ${result.bspVersion} is incompatible with client bspVersion ${config.bspVersion}")
        }
        return result
    }

    /**
     * Requests shutdown and waits for the exit frame to be sent to the transport.
     * This does not guarantee peer receipt. If the session ends first, for example because the
     * server closes its side after reading `build/exit`, this returns normally or throws the
     * terminal failure that [awaitTermination] reports. The caller may bound the wait with a
     * timeout; cancellation aborts the session without closing the borrowed transport.
     * A second call throws [SessionStateException].
     */
    public suspend fun shutdown(timeout: Duration = defaultRequestTimeout) {
        gate.beginShutdown()
        try {
            RpcCaller(connection, gate).requestUngated(BspMethods.BUILD_SHUTDOWN, null, timeout)
            lifecycle.exit { connection.notify(BspMethods.BUILD_EXIT, null) }
        } finally {
            gate.close()
            lifecycle.close()
        }
    }

    /**
     * Stop the client immediately, canceling pending requests and discarding unsent frames.
     * Use [shutdown] for orderly exit. The transport remains caller-owned.
     */
    public fun close() {
        gate.close()
        lifecycle.close()
    }

    /**
     * Waits for session jobs to stop. Returns a terminal read, write, or bounded core failure,
     * or null on EOF, explicit close, graceful exit, or parent cancellation. On EOF the session
     * first writes the replies it still owes; if that final write fails (for example because the
     * peer closed both directions while a reply was pending), the write failure is returned. The session records
     * its first observed end, then closes the core; a core failure claimed before that close
     * takes precedence. Concurrent sources do not have a global first-event ordering. The transport remains caller-owned
     * and its suspending operations must cooperate with coroutine cancellation. A caller may
     * need to close its transport to unblock a blocking read before this function can return.
     */
    public suspend fun awaitTermination(): Throwable? = lifecycle.awaitTermination()
}
