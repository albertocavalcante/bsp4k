package io.bsp4k.server

import io.bsp4k.protocol.BspDataKind
import io.bsp4k.protocol.InitializeBuildParams
import io.bsp4k.protocol.InitializeBuildResult
import kotlinx.serialization.json.JsonElement

/**
 * Runs while the server handles `build/initialize`, before it answers. Receives the client's params and may
 * set the result `data`; throwing rejects the request, the error becomes the response and the server stays
 * [BuildServerState.Uninitialized].
 */
public typealias InitializeHook = suspend InitializeResultBuilder.(params: InitializeBuildParams) -> Unit

/** Runs for a lifecycle message; the receiver reaches the running server. */
public typealias LifecycleHook = suspend BuildServerContext.() -> Unit

/**
 * Runs for `build/exit` with the process exit code BSP asks for: 0 when `build/shutdown` came first,
 * otherwise 1. It runs after queued messages were flushed and before the session closes, so it may end
 * the process; whatever it sends is flushed once more after it returns.
 */
public typealias ExitHook = suspend BuildServerContext.(exitCode: Int) -> Unit

/**
 * Application hooks for the lifecycle methods that the server handles itself.
 *
 * @property onInitialize Runs for `build/initialize`; see [InitializeHook].
 * @property onInitialized Runs once for the first `build/initialized`, before later messages are
 *   dispatched. The state is already Running while it executes, so a request that
 *   arrives meanwhile is served concurrently; launch long work, such as dependency resolution, in your own scope. A failure is dropped
 *   because notifications have no response.
 * @property onShutdown Runs for an accepted `build/shutdown`, before the server answers; throwing turns the
 *   answer into an error, and the server still refuses later requests.
 * @property onExit Runs for the first `build/exit`; see [ExitHook].
 */
public data class LifecycleHooks(
    val onInitialize: InitializeHook? = null,
    val onInitialized: LifecycleHook? = null,
    val onShutdown: LifecycleHook? = null,
    val onExit: ExitHook? = null,
)

/**
 * Registers [LifecycleHooks]; each hook may be registered once per server.
 */
@BspServerDsl
public class LifecycleHooksBuilder internal constructor() {
    private var hooks = LifecycleHooks()

    /** Registers [LifecycleHooks.onInitialize]. */
    public fun onInitialize(hook: InitializeHook) {
        require(hooks.onInitialize == null) { "onInitialize is already registered" }
        hooks = hooks.copy(onInitialize = hook)
    }

    /** Registers [LifecycleHooks.onInitialized]. */
    public fun onInitialized(hook: LifecycleHook) {
        require(hooks.onInitialized == null) { "onInitialized is already registered" }
        hooks = hooks.copy(onInitialized = hook)
    }

    /** Registers [LifecycleHooks.onShutdown]. */
    public fun onShutdown(hook: LifecycleHook) {
        require(hooks.onShutdown == null) { "onShutdown is already registered" }
        hooks = hooks.copy(onShutdown = hook)
    }

    /** Registers [LifecycleHooks.onExit]. */
    public fun onExit(hook: ExitHook) {
        require(hooks.onExit == null) { "onExit is already registered" }
        hooks = hooks.copy(onExit = hook)
    }

    internal fun build(): LifecycleHooks = hooks
}

/**
 * The receiver of [InitializeHook]: sets the `dataKind` and `data` of the `build/initialize` result.
 * BSP 2.2 defines no kinds for this payload, so any kind is the server's own.
 */
@BspServerDsl
public class InitializeResultBuilder internal constructor() {
    /** The `dataKind` of the result, or null for none. */
    public var dataKind: String? = null

    /** The raw `data` of the result, or null for none. */
    public var data: JsonElement? = null

    /** Sets [dataKind] and [data] to [value] encoded as [kind]. */
    public fun <T> data(
        kind: BspDataKind<InitializeBuildResult, T>,
        value: T,
    ) {
        dataKind = kind.kind
        data = kind.encode(value)
    }
}
