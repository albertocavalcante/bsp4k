package io.bsp4k.server

import io.bsp4k.protocol.BSP_VERSION
import io.bsp4k.protocol.BuildServerCapabilities

/**
 * Top-level builder for assembling a [BuildServerConfig] via the [buildServer] DSL.
 *
 * Handler groups follow the BSP method namespaces; repeated blocks accumulate, and each method may have one
 * handler. Every handler and hook runs with a [BuildServerContext] receiver.
 */
@BspServerDsl
public class BuildServerBuilder internal constructor() {
    private var serverName: String = "bsp4k-server"
    private var serverVersion: String = "0.1.0"
    private var bspVersion: String = BSP_VERSION
    private var capabilities = BuildServerCapabilities()
    private val registry = HandlerRegistry()
    private val lifecycleHooks = LifecycleHooksBuilder()

    /**
     * Sets the server name, version, and BSP protocol version.
     */
    public fun serverInfo(
        name: String,
        version: String,
        bspVersion: String = BSP_VERSION,
    ) {
        serverName = name
        serverVersion = version
        this.bspVersion = bspVersion
    }

    /**
     * Configures server capabilities advertised in the `build/initialize` response. Each provider needs
     * its handler, and each capability-gated handler needs its provider; see [BuildServerConfig].
     */
    public fun capabilities(block: BuildServerCapabilitiesBuilder.() -> Unit) {
        capabilities = BuildServerCapabilitiesBuilder().apply(block).build()
    }

    /** Registers hooks for `build/initialize`, `build/initialized`, `build/shutdown` and `build/exit`. */
    public fun lifecycle(block: LifecycleHooksBuilder.() -> Unit) {
        lifecycleHooks.apply(block)
    }

    /** Registers handlers for `workspace/` methods. */
    public fun workspace(block: WorkspaceHandlersBuilder.() -> Unit) {
        WorkspaceHandlersBuilder(registry).apply(block)
    }

    /** Registers handlers for the core `buildTarget/` methods. */
    public fun buildTarget(block: BuildTargetHandlersBuilder.() -> Unit) {
        BuildTargetHandlersBuilder(registry).apply(block)
    }

    /** Registers the handler for `debugSession/start`. */
    public fun debugSession(block: DebugSessionHandlersBuilder.() -> Unit) {
        DebugSessionHandlersBuilder(registry).apply(block)
    }

    /** Registers handlers for the `run/` notifications a client sends. */
    public fun run(block: RunHandlersBuilder.() -> Unit) {
        RunHandlersBuilder(registry).apply(block)
    }

    /** Registers handlers for the JVM extension. */
    public fun jvm(block: JvmHandlersBuilder.() -> Unit) {
        JvmHandlersBuilder(registry).apply(block)
    }

    /** Registers handlers for the Java extension. */
    public fun java(block: JavaHandlersBuilder.() -> Unit) {
        JavaHandlersBuilder(registry).apply(block)
    }

    /** Registers handlers for the Scala extension. */
    public fun scala(block: ScalaHandlersBuilder.() -> Unit) {
        ScalaHandlersBuilder(registry).apply(block)
    }

    /** Registers handlers for the Python extension. */
    public fun python(block: PythonHandlersBuilder.() -> Unit) {
        PythonHandlersBuilder(registry).apply(block)
    }

    /** Registers handlers for the Rust extension. */
    public fun rust(block: RustHandlersBuilder.() -> Unit) {
        RustHandlersBuilder(registry).apply(block)
    }

    /** Registers handlers for the Cargo extension. */
    public fun cargo(block: CargoHandlersBuilder.() -> Unit) {
        CargoHandlersBuilder(registry).apply(block)
    }

    /** Registers handlers for the C++ extension. */
    public fun cpp(block: CppHandlersBuilder.() -> Unit) {
        CppHandlersBuilder(registry).apply(block)
    }

    /**
     * Registers a request handler for [method] with raw JSON params, for methods outside BSP 2.2. The
     * server owns `build/initialize` and `build/shutdown`; use [lifecycle] hooks instead.
     */
    public fun onRequest(
        method: String,
        handler: ServerRequestHandler,
    ) {
        requireNotReserved(method, reservedRequests)
        registry.registerRequest(method, handler)
    }

    /**
     * Registers a notification handler for [method] with raw JSON params, for methods outside BSP 2.2.
     * The server owns `build/initialized` and `build/exit`, and jsonrpc4k consumes `$/cancelRequest`
     * before handler dispatch, so those are rejected.
     */
    public fun onNotification(
        method: String,
        handler: ServerNotificationHandler,
    ) {
        requireNotReserved(method, reservedNotifications)
        registry.registerNotification(method, handler)
    }

    internal fun build(): BuildServerConfig =
        BuildServerConfig(
            serverName = serverName,
            serverVersion = serverVersion,
            bspVersion = bspVersion,
            capabilities = capabilities,
            requestHandlers = registry.requests.toMap(),
            notificationHandlers = registry.notifications.toMap(),
            lifecycleHooks = lifecycleHooks.build(),
        )
}

/**
 * DSL entry point for creating a [BuildServerConfig]. Fails with [IllegalArgumentException] for a duplicate
 * or reserved registration, or when capabilities and handlers disagree.
 *
 * Example:
 * ```kotlin
 * val config = buildServer {
 *     serverInfo("my-build-server", "1.0.0")
 *     capabilities {
 *         compileProvider = CompileProvider(listOf("kotlin", "java"))
 *     }
 *     buildTarget {
 *         compile { params -> CompileResult(originId = params.originId, statusCode = StatusCode.Ok) }
 *     }
 * }
 * ```
 */
public fun buildServer(block: BuildServerBuilder.() -> Unit): BuildServerConfig = BuildServerBuilder().apply(block).build()
