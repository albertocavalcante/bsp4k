package io.bsp4k.server

import io.bsp4k.protocol.BspMethods
import io.bsp4k.protocol.BuildServerCapabilities
import io.bsp4k.protocol.CleanCacheParams
import io.bsp4k.protocol.CleanCacheResult
import io.bsp4k.protocol.CompileParams
import io.bsp4k.protocol.CompileProvider
import io.bsp4k.protocol.CompileResult
import io.bsp4k.protocol.DebugProvider
import io.bsp4k.protocol.DebugSessionAddress
import io.bsp4k.protocol.DebugSessionParams
import io.bsp4k.protocol.DependencyModulesParams
import io.bsp4k.protocol.DependencyModulesResult
import io.bsp4k.protocol.DependencySourcesParams
import io.bsp4k.protocol.DependencySourcesResult
import io.bsp4k.protocol.DidChangeBuildTarget
import io.bsp4k.protocol.InitializeBuildParams
import io.bsp4k.protocol.InitializeBuildResult
import io.bsp4k.protocol.InverseSourcesParams
import io.bsp4k.protocol.InverseSourcesResult
import io.bsp4k.protocol.JvmCompileClasspathParams
import io.bsp4k.protocol.JvmCompileClasspathResult
import io.bsp4k.protocol.JvmEnvironmentParams
import io.bsp4k.protocol.JvmEnvironmentResult
import io.bsp4k.protocol.LogMessageParams
import io.bsp4k.protocol.MessageType
import io.bsp4k.protocol.OutputPathsParams
import io.bsp4k.protocol.OutputPathsResult
import io.bsp4k.protocol.PublishDiagnosticsParams
import io.bsp4k.protocol.ResourcesParams
import io.bsp4k.protocol.ResourcesResult
import io.bsp4k.protocol.RunParams
import io.bsp4k.protocol.RunProvider
import io.bsp4k.protocol.RunResult
import io.bsp4k.protocol.ShowMessageParams
import io.bsp4k.protocol.SourcesParams
import io.bsp4k.protocol.SourcesResult
import io.bsp4k.protocol.TaskFinishParams
import io.bsp4k.protocol.TaskProgressParams
import io.bsp4k.protocol.TaskStartParams
import io.bsp4k.protocol.TestParams
import io.bsp4k.protocol.TestProvider
import io.bsp4k.protocol.TestResult
import io.bsp4k.protocol.WorkspaceBuildTargetsResult
import io.jsonrpc4k.core.Connection
import io.jsonrpc4k.core.JsonRpcCodec
import io.jsonrpc4k.core.JsonRpcException
import io.jsonrpc4k.core.NotificationHandler
import io.jsonrpc4k.core.RequestHandler
import io.jsonrpc4k.core.ResponseError
import io.jsonrpc4k.transport.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.serializer
import kotlin.concurrent.Volatile

/**
 * Marks DSL classes used for configuring a BSP build server.
 */
@DslMarker
public annotation class BspServerDsl

/**
 * Immutable snapshot of a fully configured build server.
 *
 * Produced by [buildServer] and consumed by [BuildServerConfig.start].
 *
 * @property serverName The display name of the build server.
 * @property serverVersion The version of the build server.
 * @property bspVersion The BSP protocol version supported by the server.
 * @property capabilities The set of BSP capabilities the server declares.
 * @property requestHandlers Handlers keyed by JSON-RPC method name for requests.
 * @property notificationHandlers Handlers keyed by JSON-RPC method name for notifications.
 */
public data class BuildServerConfig(
    val serverName: String,
    val serverVersion: String,
    val bspVersion: String,
    val capabilities: BuildServerCapabilities,
    val requestHandlers: Map<String, RequestHandler>,
    val notificationHandlers: Map<String, NotificationHandler>,
)

private val reservedRequests = setOf(BspMethods.BUILD_INITIALIZE, BspMethods.BUILD_SHUTDOWN)
private val reservedNotifications = setOf(BspMethods.BUILD_INITIALIZED, BspMethods.BUILD_EXIT, "$/cancelRequest")

private fun requireNotReserved(
    method: String,
    reserved: Set<String>,
) {
    require(method !in reserved) { "Cannot register a handler for reserved method: $method" }
}

@PublishedApi
internal fun <H> MutableMap<String, H>.putUniqueHandler(
    method: String,
    handler: H,
) {
    require(method !in this) { "Handler already registered for method: $method" }
    this[method] = handler
}

/**
 * Top-level builder for assembling a [BuildServerConfig] via the [buildServer] DSL.
 */
@BspServerDsl
public class BuildServerBuilder {
    private var serverName: String = "bsp4k-server"
    private var serverVersion: String = "0.1.0"
    private var bspVersion: String = "2.1.0"
    private var capabilities = BuildServerCapabilities()
    private val requestHandlers = mutableMapOf<String, RequestHandler>()
    private val notificationHandlers = mutableMapOf<String, NotificationHandler>()

    /**
     * Sets the server name, version, and BSP protocol version.
     */
    public fun serverInfo(
        name: String,
        version: String,
        bspVersion: String = "2.1.0",
    ) {
        serverName = name
        serverVersion = version
        this.bspVersion = bspVersion
    }

    /**
     * Configures server capabilities advertised in the `build/initialize` response.
     */
    public fun capabilities(block: BuildServerCapabilitiesBuilder.() -> Unit) {
        capabilities = BuildServerCapabilitiesBuilder().apply(block).build()
    }

    /**
     * Registers handlers for buildTarget methods.
     */
    public fun buildTarget(block: BuildTargetHandlersBuilder.() -> Unit) {
        BuildTargetHandlersBuilder(requestHandlers).apply(block)
    }

    /**
     * Registers handlers for workspace methods.
     */
    public fun workspace(block: WorkspaceHandlersBuilder.() -> Unit) {
        WorkspaceHandlersBuilder(requestHandlers).apply(block)
    }

    /**
     * Registers handlers for JVM extension methods.
     */
    public fun jvm(block: JvmHandlersBuilder.() -> Unit) {
        JvmHandlersBuilder(requestHandlers).apply(block)
    }

    /**
     * Registers a custom request handler for the given JSON-RPC [method].
     */
    public fun onRequest(
        method: String,
        handler: RequestHandler,
    ) {
        requireNotReserved(method, reservedRequests)
        requestHandlers.putUniqueHandler(method, handler)
    }

    /**
     * Registers a custom notification handler for the given JSON-RPC [method].
     */
    public fun onNotification(
        method: String,
        handler: NotificationHandler,
    ) {
        requireNotReserved(method, reservedNotifications)
        notificationHandlers.putUniqueHandler(method, handler)
    }

    internal fun build(): BuildServerConfig =
        BuildServerConfig(
            serverName = serverName,
            serverVersion = serverVersion,
            bspVersion = bspVersion,
            capabilities = capabilities,
            requestHandlers = requestHandlers.toMap(),
            notificationHandlers = notificationHandlers.toMap(),
        )
}

// =============================================================================
// BuildServerCapabilitiesBuilder
// =============================================================================

/**
 * Builder for constructing [BuildServerCapabilities] advertised during initialization.
 */
@BspServerDsl
public class BuildServerCapabilitiesBuilder {
    /** Provider for compile requests. */
    public var compileProvider: CompileProvider? = null

    /** Provider for test requests. */
    public var testProvider: TestProvider? = null

    /** Provider for run requests. */
    public var runProvider: RunProvider? = null

    /** Provider for debug requests. */
    public var debugProvider: DebugProvider? = null

    /** Whether the server supports inverse sources. */
    public var inverseSourcesProvider: Boolean? = null

    /** Whether the server supports dependency sources. */
    public var dependencySourcesProvider: Boolean? = null

    /** Whether the server supports dependency modules. */
    public var dependencyModulesProvider: Boolean? = null

    /** Whether the server supports resources. */
    public var resourcesProvider: Boolean? = null

    /** Whether the server supports output paths. */
    public var outputPathsProvider: Boolean? = null

    /** Whether the server supports build target changed notifications. */
    public var buildTargetChangedProvider: Boolean? = null

    /** Whether the server supports JVM run environment. */
    public var jvmRunEnvironmentProvider: Boolean? = null

    /** Whether the server supports JVM test environment. */
    public var jvmTestEnvironmentProvider: Boolean? = null

    /** Whether the server supports JVM compile classpath. */
    public var jvmCompileClasspathProvider: Boolean? = null

    /** Whether the server supports reload. */
    public var canReload: Boolean? = null

    internal fun build(): BuildServerCapabilities =
        BuildServerCapabilities(
            compileProvider = compileProvider,
            testProvider = testProvider,
            runProvider = runProvider,
            debugProvider = debugProvider,
            inverseSourcesProvider = inverseSourcesProvider,
            dependencySourcesProvider = dependencySourcesProvider,
            dependencyModulesProvider = dependencyModulesProvider,
            resourcesProvider = resourcesProvider,
            outputPathsProvider = outputPathsProvider,
            buildTargetChangedProvider = buildTargetChangedProvider,
            jvmRunEnvironmentProvider = jvmRunEnvironmentProvider,
            jvmTestEnvironmentProvider = jvmTestEnvironmentProvider,
            jvmCompileClasspathProvider = jvmCompileClasspathProvider,
            canReload = canReload,
        )
}

// =============================================================================
// HandlersBuilder (abstract base)
// =============================================================================

/**
 * Abstract base for handler-builder classes that share request registration logic.
 */
@BspServerDsl
public abstract class HandlersBuilder internal constructor(
    @PublishedApi internal val json: Json,
    @PublishedApi internal val requestHandlers: MutableMap<String, RequestHandler>,
) {
    /**
     * Registers a typed request handler that decodes [P] from JSON, invokes
     * [handler], and encodes the nullable result [R] back to JSON.
     */
    @PublishedApi
    internal inline fun <reified P, reified R> registerRequest(
        method: String,
        crossinline handler: suspend (P) -> R?,
    ) {
        val paramSerializer = serializer<P>()
        val resultSerializer = serializer<R>()
        requestHandlers.putUniqueHandler(
            method,
            RequestHandler { params ->
                val typedParams: P =
                    params?.let { json.decodeFromJsonElement(paramSerializer, it) }
                        ?: throw JsonRpcException.invalidParams("Missing params for $method")
                val result = handler(typedParams)
                result?.let { json.encodeToJsonElement(resultSerializer, it) }
            },
        )
    }

    /**
     * Registers a request handler that takes no parameters and returns [R].
     */
    @PublishedApi
    internal inline fun <reified R> registerRequestNoParams(
        method: String,
        crossinline handler: suspend () -> R?,
    ) {
        val resultSerializer = serializer<R>()
        requestHandlers.putUniqueHandler(
            method,
            RequestHandler { _ ->
                val result = handler()
                result?.let { json.encodeToJsonElement(resultSerializer, it) }
            },
        )
    }
}

// =============================================================================
// BuildTargetHandlersBuilder
// =============================================================================

/**
 * Builder for buildTarget request handlers.
 */
@BspServerDsl
public class BuildTargetHandlersBuilder(
    requestHandlers: MutableMap<String, RequestHandler>,
) : HandlersBuilder(JsonRpcCodec.defaultJson, requestHandlers) {
    /** Registers a handler for `buildTarget/sources` requests. */
    public fun sources(handler: suspend (SourcesParams) -> SourcesResult) {
        registerRequest(BspMethods.BUILD_TARGET_SOURCES, handler)
    }

    /** Registers a handler for `buildTarget/inverseSources` requests. */
    public fun inverseSources(handler: suspend (InverseSourcesParams) -> InverseSourcesResult) {
        registerRequest(BspMethods.BUILD_TARGET_INVERSE_SOURCES, handler)
    }

    /** Registers a handler for `buildTarget/dependencySources` requests. */
    public fun dependencySources(handler: suspend (DependencySourcesParams) -> DependencySourcesResult) {
        registerRequest(BspMethods.BUILD_TARGET_DEPENDENCY_SOURCES, handler)
    }

    /** Registers a handler for `buildTarget/dependencyModules` requests. */
    public fun dependencyModules(handler: suspend (DependencyModulesParams) -> DependencyModulesResult) {
        registerRequest(BspMethods.BUILD_TARGET_DEPENDENCY_MODULES, handler)
    }

    /** Registers a handler for `buildTarget/resources` requests. */
    public fun resources(handler: suspend (ResourcesParams) -> ResourcesResult) {
        registerRequest(BspMethods.BUILD_TARGET_RESOURCES, handler)
    }

    /** Registers a handler for `buildTarget/outputPaths` requests. */
    public fun outputPaths(handler: suspend (OutputPathsParams) -> OutputPathsResult) {
        registerRequest(BspMethods.BUILD_TARGET_OUTPUT_PATHS, handler)
    }

    /** Registers a handler for `buildTarget/compile` requests. */
    public fun compile(handler: suspend (CompileParams) -> CompileResult) {
        registerRequest(BspMethods.BUILD_TARGET_COMPILE, handler)
    }

    /** Registers a handler for `buildTarget/test` requests. */
    public fun test(handler: suspend (TestParams) -> TestResult) {
        registerRequest(BspMethods.BUILD_TARGET_TEST, handler)
    }

    /** Registers a handler for `buildTarget/run` requests. */
    public fun run(handler: suspend (RunParams) -> RunResult) {
        registerRequest(BspMethods.BUILD_TARGET_RUN, handler)
    }

    /** Registers a handler for `buildTarget/cleanCache` requests. */
    public fun cleanCache(handler: suspend (CleanCacheParams) -> CleanCacheResult) {
        registerRequest(BspMethods.BUILD_TARGET_CLEAN_CACHE, handler)
    }

    /** Registers a handler for `buildTarget/debugSession` requests. */
    public fun debugSession(handler: suspend (DebugSessionParams) -> DebugSessionAddress) {
        registerRequest(BspMethods.BUILD_TARGET_DEBUG_SESSION, handler)
    }
}

// =============================================================================
// WorkspaceHandlersBuilder
// =============================================================================

/**
 * Builder for workspace request handlers.
 */
@BspServerDsl
public class WorkspaceHandlersBuilder(
    requestHandlers: MutableMap<String, RequestHandler>,
) : HandlersBuilder(JsonRpcCodec.defaultJson, requestHandlers) {
    /** Registers a handler for `workspace/buildTargets` requests. */
    public fun buildTargets(handler: suspend () -> WorkspaceBuildTargetsResult) {
        registerRequestNoParams(BspMethods.WORKSPACE_BUILD_TARGETS, handler)
    }

    /** Registers a handler for `workspace/reload` requests. */
    public fun reload(handler: suspend () -> Unit) {
        requestHandlers.putUniqueHandler(
            BspMethods.WORKSPACE_RELOAD,
            RequestHandler { _ ->
                handler()
                null
            },
        )
    }
}

// =============================================================================
// JvmHandlersBuilder
// =============================================================================

/**
 * Builder for JVM extension request handlers.
 */
@BspServerDsl
public class JvmHandlersBuilder(
    requestHandlers: MutableMap<String, RequestHandler>,
) : HandlersBuilder(JsonRpcCodec.defaultJson, requestHandlers) {
    /** Registers a handler for `buildTarget/jvmRunEnvironment` requests. */
    public fun runEnvironment(handler: suspend (JvmEnvironmentParams) -> JvmEnvironmentResult) {
        registerRequest(BspMethods.BUILD_TARGET_JVM_RUN_ENVIRONMENT, handler)
    }

    /** Registers a handler for `buildTarget/jvmTestEnvironment` requests. */
    public fun testEnvironment(handler: suspend (JvmEnvironmentParams) -> JvmEnvironmentResult) {
        registerRequest(BspMethods.BUILD_TARGET_JVM_TEST_ENVIRONMENT, handler)
    }

    /** Registers a handler for `buildTarget/jvmCompileClasspath` requests. */
    public fun compileClasspath(handler: suspend (JvmCompileClasspathParams) -> JvmCompileClasspathResult) {
        registerRequest(BspMethods.BUILD_TARGET_JVM_COMPILE_CLASSPATH, handler)
    }
}

// =============================================================================
// BuildServer
// =============================================================================

/**
 * A running build server instance.
 *
 * Created via [BuildServerConfig.start]. Manages the BSP lifecycle
 * (build/initialize, build/initialized, build/shutdown, build/exit) and
 * delegates registered handlers to the underlying [Connection].
 *
 * @property client A proxy for sending notifications to the connected BSP client.
 */
public class BuildServer internal constructor(
    private val config: BuildServerConfig,
    private val connection: Connection,
    private val lifecycle: SessionLifecycle,
) {
    @Volatile
    private var initialized = false

    @Volatile
    private var shutdownRequested = false

    /**
     * Client proxy for sending notifications to the client.
     */
    public val client: BuildClientProxy = BuildClientProxy(connection)

    /**
     * Validates that the server is initialized and not shutting down.
     */
    private fun checkServerState(method: String) {
        if (shutdownRequested) {
            throw JsonRpcException(
                ResponseError.INVALID_REQUEST,
                "Server is shutting down, cannot process request: $method",
            )
        }
        if (!initialized) {
            throw JsonRpcException(
                ResponseError.SERVER_NOT_INITIALIZED,
                "Server not initialized",
            )
        }
    }

    internal suspend fun setup() {
        connection.onRequest(BspMethods.BUILD_INITIALIZE) { params ->
            handleInitialize(params)
        }

        connection.onNotification(BspMethods.BUILD_INITIALIZED) { _ ->
            initialized = true
        }

        connection.onRequest(BspMethods.BUILD_SHUTDOWN) { _ ->
            shutdownRequested = true
            null
        }

        connection.onNotification(BspMethods.BUILD_EXIT) { _ ->
            try {
                connection.flushOutgoing()
            } finally {
                lifecycle.close()
            }
        }

        // Register user-provided request handlers with state checks
        config.requestHandlers.forEach { (method, handler) ->
            connection.onRequest(method) { params ->
                checkServerState(method)
                handler.handle(params)
            }
        }

        // Register user-provided notification handlers with state checks
        config.notificationHandlers.forEach { (method, handler) ->
            connection.onNotification(method) { params ->
                checkServerState(method)
                handler.handle(params)
            }
        }
    }

    /** Stops immediately, canceling pending work and discarding unsent frames; the transport remains caller-owned. */
    public fun close(): Unit = lifecycle.close()

    /**
     * Waits for session jobs to stop. Returns the first read or write failure, or null on EOF,
     * explicit close, graceful exit, or parent cancellation. The transport remains caller-owned
     * and its suspending operations must cooperate with coroutine cancellation.
     */
    public suspend fun awaitTermination(): Throwable? = lifecycle.awaitTermination()

    private fun handleInitialize(params: JsonElement?): JsonElement {
        val json = JsonRpcCodec.defaultJson
        if (params != null) {
            json.decodeFromJsonElement(InitializeBuildParams.serializer(), params)
        }

        val result =
            InitializeBuildResult(
                displayName = config.serverName,
                version = config.serverVersion,
                bspVersion = config.bspVersion,
                capabilities = config.capabilities,
            )

        return json.encodeToJsonElement(InitializeBuildResult.serializer(), result)
    }
}

// =============================================================================
// BuildClientProxy
// =============================================================================

/**
 * Proxy for sending notifications to the BSP client.
 *
 * An instance is available via [BuildServer.client] after the server is started.
 */
public class BuildClientProxy internal constructor(
    @PublishedApi internal val connection: Connection,
) {
    @PublishedApi
    internal val json: Json = JsonRpcCodec.defaultJson

    @PublishedApi
    internal suspend inline fun <reified P> sendNotification(
        method: String,
        params: P,
    ) {
        connection.notify(method, json.encodeToJsonElement(serializer<P>(), params))
    }

    /** Sends a `build/logMessage` notification. */
    public suspend fun logMessage(params: LogMessageParams) {
        sendNotification(BspMethods.BUILD_LOG_MESSAGE, params)
    }

    /** Sends a `build/logMessage` notification with the given [type] and [message]. */
    public suspend fun logMessage(
        type: MessageType,
        message: String,
    ) {
        logMessage(LogMessageParams(type = type, message = message))
    }

    /** Sends a `build/showMessage` notification. */
    public suspend fun showMessage(params: ShowMessageParams) {
        sendNotification(BspMethods.BUILD_SHOW_MESSAGE, params)
    }

    /** Sends a `build/showMessage` notification with the given [type] and [message]. */
    public suspend fun showMessage(
        type: MessageType,
        message: String,
    ) {
        showMessage(ShowMessageParams(type = type, message = message))
    }

    /** Sends a `build/publishDiagnostics` notification. */
    public suspend fun publishDiagnostics(params: PublishDiagnosticsParams) {
        sendNotification(BspMethods.BUILD_PUBLISH_DIAGNOSTICS, params)
    }

    /** Sends a `buildTarget/didChange` notification. */
    public suspend fun buildTargetDidChange(params: DidChangeBuildTarget) {
        sendNotification(BspMethods.BUILD_TARGET_DID_CHANGE, params)
    }

    /** Sends a `build/taskStart` notification. */
    public suspend fun taskStart(params: TaskStartParams) {
        sendNotification(BspMethods.BUILD_TASK_START, params)
    }

    /** Sends a `build/taskProgress` notification. */
    public suspend fun taskProgress(params: TaskProgressParams) {
        sendNotification(BspMethods.BUILD_TASK_PROGRESS, params)
    }

    /** Sends a `build/taskFinish` notification. */
    public suspend fun taskFinish(params: TaskFinishParams) {
        sendNotification(BspMethods.BUILD_TASK_FINISH, params)
    }
}

// =============================================================================
// DSL entry point & start
// =============================================================================

/**
 * DSL entry point for creating a [BuildServerConfig].
 *
 * Example:
 * ```kotlin
 * val config = buildServer {
 *     serverInfo("my-build-server", "1.0.0")
 *     capabilities {
 *         compileProvider = CompileProvider(listOf("kotlin", "java"))
 *     }
 *     buildTarget {
 *         compile { params -> CompileResult(statusCode = StatusCode.Ok) }
 *     }
 * }
 * ```
 */
public fun buildServer(block: BuildServerBuilder.() -> Unit): BuildServerConfig = BuildServerBuilder().apply(block).build()

/**
 * Starts a build server with the given [transport] and optional [scope].
 *
 * The returned [BuildServer] is fully wired: incoming transport data is
 * decoded and dispatched, and outgoing messages are forwarded to the transport.
 * The supplied scope and transport remain caller-owned; closing a session does not close the transport.
 */
public suspend fun BuildServerConfig.start(
    transport: Transport,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
): BuildServer {
    requestHandlers.keys.forEach { requireNotReserved(it, reservedRequests) }
    notificationHandlers.keys.forEach { requireNotReserved(it, reservedNotifications) }
    val lifecycle = SessionLifecycle(scope)
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
