package io.bsp4k.client

import io.bsp4k.protocol.BspMethods
import io.bsp4k.protocol.BuildClientCapabilities
import io.bsp4k.protocol.CleanCacheParams
import io.bsp4k.protocol.CleanCacheResult
import io.bsp4k.protocol.CompileParams
import io.bsp4k.protocol.CompileResult
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
import io.bsp4k.protocol.OutputPathsParams
import io.bsp4k.protocol.OutputPathsResult
import io.bsp4k.protocol.PublishDiagnosticsParams
import io.bsp4k.protocol.ResourcesParams
import io.bsp4k.protocol.ResourcesResult
import io.bsp4k.protocol.RunParams
import io.bsp4k.protocol.RunResult
import io.bsp4k.protocol.ShowMessageParams
import io.bsp4k.protocol.SourcesParams
import io.bsp4k.protocol.SourcesResult
import io.bsp4k.protocol.TaskFinishParams
import io.bsp4k.protocol.TaskProgressParams
import io.bsp4k.protocol.TaskStartParams
import io.bsp4k.protocol.TestParams
import io.bsp4k.protocol.TestResult
import io.bsp4k.protocol.WorkspaceBuildTargetsResult
import io.jsonrpc4k.core.BoundedDispatchPolicy
import io.jsonrpc4k.core.Connection
import io.jsonrpc4k.core.InboundResourceLimits
import io.jsonrpc4k.core.JsonRpcCodec
import io.jsonrpc4k.core.NotificationHandler
import io.jsonrpc4k.core.OutboundResourceLimits
import io.jsonrpc4k.transport.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer

/**
 * Marks DSL classes used for configuring a BSP build client.
 */
@DslMarker
public annotation class BspClientDsl

/**
 * Configuration for a build client.
 */
public data class BuildClientConfig(
    val displayName: String,
    val version: String,
    val bspVersion: String,
    val rootUri: String,
    val capabilities: BuildClientCapabilities,
    val notificationHandlers: Map<String, NotificationHandler>,
)

private const val CANCEL_REQUEST = "$/cancelRequest"

private fun requireNotReserved(method: String) {
    require(method != CANCEL_REQUEST) { "Cannot register a handler for reserved method: $method" }
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
 * Builder for build client configuration.
 */
@BspClientDsl
public class BuildClientBuilder {
    private var displayName: String = "bsp4k-client"
    private var version: String = "0.1.0"
    private var bspVersion: String = "2.1.0"
    private var rootUri: String = ""
    private var capabilities = BuildClientCapabilities(languageIds = emptyList())
    private val notificationHandlers = mutableMapOf<String, NotificationHandler>()
    private val json = JsonRpcCodec.defaultJson

    private inline fun <reified P> handleNotification(
        method: String,
        crossinline extract: suspend (P) -> Unit,
    ) {
        notificationHandlers.putUniqueHandler(
            method,
            NotificationHandler { params ->
                val typed = params?.let { json.decodeFromJsonElement(serializer<P>(), it) }
                if (typed != null) extract(typed)
            },
        )
    }

    /**
     * Set client information.
     */
    public fun clientInfo(
        name: String,
        version: String,
        bspVersion: String = "2.1.0",
    ) {
        displayName = name
        this.version = version
        this.bspVersion = bspVersion
    }

    /**
     * Set the root URI for the workspace.
     */
    public fun rootUri(uri: String) {
        rootUri = uri
    }

    /**
     * Configure client capabilities.
     */
    public fun capabilities(languageIds: List<String>) {
        capabilities = BuildClientCapabilities(languageIds = languageIds)
    }

    /**
     * Handle `build/logMessage` notifications.
     */
    public fun onLogMessage(handler: suspend (LogMessageParams) -> Unit) {
        handleNotification(BspMethods.BUILD_LOG_MESSAGE, handler)
    }

    /**
     * Handle `build/showMessage` notifications.
     */
    public fun onShowMessage(handler: suspend (ShowMessageParams) -> Unit) {
        handleNotification(BspMethods.BUILD_SHOW_MESSAGE, handler)
    }

    /**
     * Handle `build/publishDiagnostics` notifications.
     */
    public fun onPublishDiagnostics(handler: suspend (PublishDiagnosticsParams) -> Unit) {
        handleNotification(BspMethods.BUILD_PUBLISH_DIAGNOSTICS, handler)
    }

    /**
     * Handle `buildTarget/didChange` notifications.
     */
    public fun onBuildTargetDidChange(handler: suspend (DidChangeBuildTarget) -> Unit) {
        handleNotification(BspMethods.BUILD_TARGET_DID_CHANGE, handler)
    }

    /**
     * Handle `build/taskStart` notifications.
     */
    public fun onTaskStart(handler: suspend (TaskStartParams) -> Unit) {
        handleNotification(BspMethods.BUILD_TASK_START, handler)
    }

    /**
     * Handle `build/taskProgress` notifications.
     */
    public fun onTaskProgress(handler: suspend (TaskProgressParams) -> Unit) {
        handleNotification(BspMethods.BUILD_TASK_PROGRESS, handler)
    }

    /**
     * Handle `build/taskFinish` notifications.
     */
    public fun onTaskFinish(handler: suspend (TaskFinishParams) -> Unit) {
        handleNotification(BspMethods.BUILD_TASK_FINISH, handler)
    }

    /**
     * Add a custom notification handler.
     */
    public fun onNotification(
        method: String,
        handler: NotificationHandler,
    ) {
        requireNotReserved(method)
        notificationHandlers.putUniqueHandler(method, handler)
    }

    internal fun build(): BuildClientConfig =
        BuildClientConfig(
            displayName = displayName,
            version = version,
            bspVersion = bspVersion,
            rootUri = rootUri,
            capabilities = capabilities,
            notificationHandlers = notificationHandlers.toMap(),
        )
}

/**
 * A running build client session.
 */
public class BuildClientSession internal constructor(
    private val config: BuildClientConfig,
    private val connection: Connection,
    private val lifecycle: SessionLifecycle,
) {
    private val json = JsonRpcCodec.defaultJson
    private val shutdownStarted = MutableStateFlow(false)

    /**
     * Server proxy for sending requests to the build server.
     */
    public val server: BuildServerProxy = BuildServerProxy(connection)

    internal suspend fun setup() {
        config.notificationHandlers.forEach { (method, handler) ->
            connection.onNotification(method, handler)
        }
    }

    /**
     * Initialize the connection with the build server.
     */
    public suspend fun initialize(): InitializeBuildResult {
        val params =
            InitializeBuildParams(
                displayName = config.displayName,
                version = config.version,
                bspVersion = config.bspVersion,
                rootUri = config.rootUri,
                capabilities = config.capabilities,
            )

        val result =
            connection.request<InitializeBuildResult>(
                BspMethods.BUILD_INITIALIZE,
                json.encodeToJsonElement(InitializeBuildParams.serializer(), params),
            )

        // Send initialized notification
        connection.notify(BspMethods.BUILD_INITIALIZED, null)

        return result?.let {
            json.decodeFromJsonElement(InitializeBuildResult.serializer(), it)
        } ?: error("build/initialize returned null")
    }

    /**
     * Requests shutdown and waits for the exit frame to be sent to the transport.
     * This does not guarantee peer receipt. If the session ends first, for example because the
     * server closes its side after reading `build/exit`, this returns normally or throws the
     * terminal failure that [awaitTermination] reports. The caller may bound the wait with a
     * timeout; cancellation aborts the session without closing the borrowed transport.
     */
    public suspend fun shutdown() {
        check(shutdownStarted.compareAndSet(false, true)) { "Shutdown already started" }
        try {
            connection.request<JsonElement?>(BspMethods.BUILD_SHUTDOWN, null)
            lifecycle.exit { connection.notify(BspMethods.BUILD_EXIT, null) }
        } finally {
            lifecycle.close()
        }
    }

    /**
     * Stop the client immediately, canceling pending requests and discarding unsent frames.
     * Use [shutdown] for orderly exit. The transport remains caller-owned.
     */
    public fun close() {
        lifecycle.close()
    }

    /**
     * Waits for session jobs to stop. Returns a terminal read, write, or bounded core failure,
     * or null on EOF, explicit close, graceful exit, or parent cancellation. The session records
     * its first observed end, then closes the core; a core failure claimed before that close
     * takes precedence. Concurrent sources do not have a global first-event ordering. The transport remains caller-owned
     * and its suspending operations must cooperate with coroutine cancellation. A caller may
     * need to close its transport to unblock a blocking read before this function can return.
     */
    public suspend fun awaitTermination(): Throwable? = lifecycle.awaitTermination()
}

/**
 * Proxy for sending requests to the build server.
 */
public class BuildServerProxy internal constructor(
    private val connection: Connection,
) {
    private val json = JsonRpcCodec.defaultJson

    private suspend inline fun <reified P, reified R> request(
        method: String,
        params: P,
    ): R? {
        val jsonParams = json.encodeToJsonElement(serializer<P>(), params)
        val result = connection.request<JsonElement>(method, jsonParams)
        return result?.let { json.decodeFromJsonElement(serializer<R>(), it) }
    }

    // ===== Workspace =====

    /** Request all workspace build targets. */
    public suspend fun workspaceBuildTargets(): WorkspaceBuildTargetsResult? {
        val result = connection.request<JsonElement>(BspMethods.WORKSPACE_BUILD_TARGETS, null)
        return result?.let { json.decodeFromJsonElement(WorkspaceBuildTargetsResult.serializer(), it) }
    }

    /** Request the server to reload the workspace. */
    public suspend fun workspaceReload() {
        connection.request<JsonElement?>(BspMethods.WORKSPACE_RELOAD, null)
    }

    // ===== Build Target =====

    /** Request sources for given build targets. */
    public suspend fun sources(params: SourcesParams): SourcesResult? = request(BspMethods.BUILD_TARGET_SOURCES, params)

    /** Request inverse sources for a text document. */
    public suspend fun inverseSources(params: InverseSourcesParams): InverseSourcesResult? =
        request(BspMethods.BUILD_TARGET_INVERSE_SOURCES, params)

    /** Request dependency sources for given build targets. */
    public suspend fun dependencySources(params: DependencySourcesParams): DependencySourcesResult? =
        request(BspMethods.BUILD_TARGET_DEPENDENCY_SOURCES, params)

    /** Request dependency modules for given build targets. */
    public suspend fun dependencyModules(params: DependencyModulesParams): DependencyModulesResult? =
        request(BspMethods.BUILD_TARGET_DEPENDENCY_MODULES, params)

    /** Request resources for given build targets. */
    public suspend fun resources(params: ResourcesParams): ResourcesResult? = request(BspMethods.BUILD_TARGET_RESOURCES, params)

    /** Request output paths for given build targets. */
    public suspend fun outputPaths(params: OutputPathsParams): OutputPathsResult? = request(BspMethods.BUILD_TARGET_OUTPUT_PATHS, params)

    /** Compile given build targets. */
    public suspend fun compile(params: CompileParams): CompileResult? = request(BspMethods.BUILD_TARGET_COMPILE, params)

    /** Run tests for given build targets. */
    public suspend fun test(params: TestParams): TestResult? = request(BspMethods.BUILD_TARGET_TEST, params)

    /** Run a build target. */
    public suspend fun run(params: RunParams): RunResult? = request(BspMethods.BUILD_TARGET_RUN, params)

    /** Clean cache for given build targets. */
    public suspend fun cleanCache(params: CleanCacheParams): CleanCacheResult? = request(BspMethods.BUILD_TARGET_CLEAN_CACHE, params)

    /** Start a debug session for a build target. */
    public suspend fun debugSession(params: DebugSessionParams): DebugSessionAddress? =
        request(BspMethods.BUILD_TARGET_DEBUG_SESSION, params)

    // ===== JVM Extensions =====

    /** Request JVM run environment for given build targets. */
    public suspend fun jvmRunEnvironment(params: JvmEnvironmentParams): JvmEnvironmentResult? =
        request(BspMethods.BUILD_TARGET_JVM_RUN_ENVIRONMENT, params)

    /** Request JVM test environment for given build targets. */
    public suspend fun jvmTestEnvironment(params: JvmEnvironmentParams): JvmEnvironmentResult? =
        request(BspMethods.BUILD_TARGET_JVM_TEST_ENVIRONMENT, params)

    /** Request JVM compile classpath for given build targets. */
    public suspend fun jvmCompileClasspath(params: JvmCompileClasspathParams): JvmCompileClasspathResult? =
        request(BspMethods.BUILD_TARGET_JVM_COMPILE_CLASSPATH, params)
}

/**
 * DSL entry point for creating a build client.
 */
public fun buildClient(block: BuildClientBuilder.() -> Unit): BuildClientConfig = BuildClientBuilder().apply(block).build()

/**
 * Start a build client session with the given configuration and transport.
 * The supplied scope and transport remain caller-owned; closing a session does not close the transport.
 */
public suspend fun BuildClientConfig.start(
    transport: Transport,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
): BuildClientSession = startSession(transport, SessionLifecycle(scope))

/**
 * Starts an explicitly bounded session using the fixed default JSON codec.
 * Limits are wire-size proxies, not a total heap bound. Typed payload conversion to JSON happens
 * before outbound admission and is outside these limits. The supplied scope and transport remain
 * caller-owned. A transport collector must not await reentrant sends on the same connection.
 */
public suspend fun BuildClientConfig.start(
    transport: Transport,
    policy: BoundedDispatchPolicy,
    inboundLimits: InboundResourceLimits,
    outboundLimits: OutboundResourceLimits,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
): BuildClientSession = startSession(transport, SessionLifecycle(scope) { Connection(policy, inboundLimits, outboundLimits, it) })

private suspend fun BuildClientConfig.startSession(
    transport: Transport,
    lifecycle: SessionLifecycle,
): BuildClientSession {
    val session = BuildClientSession(this, lifecycle.connection, lifecycle)
    try {
        notificationHandlers.keys.forEach(::requireNotReserved)
        session.setup()
        lifecycle.start(transport)
    } catch (failure: Throwable) {
        lifecycle.fail(failure)
        throw failure
    }
    return session
}
