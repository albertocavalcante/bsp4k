package io.bsp4k.server

import io.bsp4k.protocol.BspMethods
import io.bsp4k.protocol.BuildServerCapabilities
import kotlinx.serialization.json.JsonElement

/**
 * Marks DSL classes used for configuring a BSP build server.
 */
@DslMarker
public annotation class BspServerDsl

/** Handles one request method from raw JSON params; the receiver reaches the running server. */
public typealias ServerRequestHandler = suspend BuildServerContext.(params: JsonElement?) -> JsonElement?

/** Handles one notification method from raw JSON params; the receiver reaches the running server. */
public typealias ServerNotificationHandler = suspend BuildServerContext.(params: JsonElement?) -> Unit

/**
 * Immutable snapshot of a fully configured build server.
 *
 * Produced by [buildServer] and consumed by [BuildServerConfig.start]. Construction, including [copy],
 * fails with [IllegalArgumentException] when a handler targets a method the server owns
 * (`build/initialize`, `build/initialized`, `build/shutdown`, `build/exit`, `$/cancelRequest`) or when
 * [capabilities] and the handlers disagree: a declared provider needs its handler, and a handler for a
 * method that BSP gates behind a capability needs that capability.
 *
 * @property serverName The display name of the build server.
 * @property serverVersion The version of the build server.
 * @property bspVersion The BSP protocol version supported by the server.
 * @property capabilities The set of BSP capabilities the server declares.
 * @property requestHandlers Handlers keyed by JSON-RPC method name for requests.
 * @property notificationHandlers Handlers keyed by JSON-RPC method name for notifications.
 * @property lifecycleHooks Application hooks for the lifecycle methods the server handles itself.
 */
public data class BuildServerConfig(
    val serverName: String,
    val serverVersion: String,
    val bspVersion: String,
    val capabilities: BuildServerCapabilities,
    val requestHandlers: Map<String, ServerRequestHandler>,
    val notificationHandlers: Map<String, ServerNotificationHandler>,
    val lifecycleHooks: LifecycleHooks,
) {
    init {
        requestHandlers.keys.forEach { requireNotReserved(it, reservedRequests) }
        notificationHandlers.keys.forEach { requireNotReserved(it, reservedNotifications) }
        requireConsistentCapabilities(capabilities, requestHandlers.keys)
    }
}

internal val reservedRequests: Set<String> = setOf(BspMethods.BUILD_INITIALIZE, BspMethods.BUILD_SHUTDOWN)

internal val reservedNotifications: Set<String> =
    setOf(BspMethods.BUILD_INITIALIZED, BspMethods.BUILD_EXIT, BspMethods.CANCEL_REQUEST)

internal fun requireNotReserved(
    method: String,
    reserved: Set<String>,
) {
    require(method !in reserved) { "Cannot register a handler for reserved method: $method" }
}

/** A request method that BSP gates behind a server capability. */
private class CapabilityBinding(
    val method: String,
    val capability: String,
    val declared: (BuildServerCapabilities) -> Boolean,
)

private val capabilityBindings: List<CapabilityBinding> =
    listOf(
        CapabilityBinding(BspMethods.BUILD_TARGET_COMPILE, "compileProvider") { it.compileProvider != null },
        CapabilityBinding(BspMethods.BUILD_TARGET_TEST, "testProvider") { it.testProvider != null },
        CapabilityBinding(BspMethods.BUILD_TARGET_RUN, "runProvider") { it.runProvider != null },
        CapabilityBinding(BspMethods.DEBUG_SESSION_START, "debugProvider") { it.debugProvider != null },
        CapabilityBinding(BspMethods.BUILD_TARGET_DESTINATIONS, "buildTargetDestinationsProvider") {
            it.buildTargetDestinationsProvider
        },
        CapabilityBinding(BspMethods.BUILD_TARGET_INVERSE_SOURCES, "inverseSourcesProvider") { it.inverseSourcesProvider },
        CapabilityBinding(BspMethods.BUILD_TARGET_DEPENDENCY_SOURCES, "dependencySourcesProvider") {
            it.dependencySourcesProvider
        },
        CapabilityBinding(BspMethods.BUILD_TARGET_DEPENDENCY_MODULES, "dependencyModulesProvider") {
            it.dependencyModulesProvider
        },
        CapabilityBinding(BspMethods.BUILD_TARGET_RESOURCES, "resourcesProvider") { it.resourcesProvider },
        CapabilityBinding(BspMethods.BUILD_TARGET_OUTPUT_PATHS, "outputPathsProvider") { it.outputPathsProvider },
        CapabilityBinding(BspMethods.BUILD_TARGET_JVM_RUN_ENVIRONMENT, "jvmRunEnvironmentProvider") {
            it.jvmRunEnvironmentProvider
        },
        CapabilityBinding(BspMethods.BUILD_TARGET_JVM_TEST_ENVIRONMENT, "jvmTestEnvironmentProvider") {
            it.jvmTestEnvironmentProvider
        },
        CapabilityBinding(BspMethods.BUILD_TARGET_JVM_COMPILE_CLASSPATH, "jvmCompileClasspathProvider") {
            it.jvmCompileClasspathProvider
        },
        CapabilityBinding(BspMethods.WORKSPACE_CARGO_FEATURES_STATE, "cargoFeaturesProvider") { it.cargoFeaturesProvider },
        CapabilityBinding(BspMethods.WORKSPACE_SET_CARGO_FEATURES, "cargoFeaturesProvider") { it.cargoFeaturesProvider },
        CapabilityBinding(BspMethods.WORKSPACE_RELOAD, "canReload") { it.canReload },
    )

/** Fails fast when a declared capability lacks its handler or a gated handler lacks its capability. */
private fun requireConsistentCapabilities(
    capabilities: BuildServerCapabilities,
    methods: Set<String>,
) {
    val problems =
        capabilityBindings.mapNotNull { binding ->
            val declared = binding.declared(capabilities)
            val handled = binding.method in methods
            when {
                declared && !handled -> "${binding.capability} is declared but ${binding.method} has no handler"
                handled && !declared -> "${binding.method} has a handler but ${binding.capability} is not declared"
                else -> null
            }
        }
    require(problems.isEmpty()) {
        "Capabilities and handlers disagree (declare the capability in capabilities { } and register " +
            "every handler it covers, for example cargo { featuresState; setFeatures }): ${problems.joinToString("; ")}"
    }
}
