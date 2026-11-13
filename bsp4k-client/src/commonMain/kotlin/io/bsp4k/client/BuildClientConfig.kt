package io.bsp4k.client

import io.bsp4k.protocol.BuildClientCapabilities
import io.jsonrpc4k.core.BoundedDispatchPolicy
import io.jsonrpc4k.core.Connection
import io.jsonrpc4k.core.InboundResourceLimits
import io.jsonrpc4k.core.NotificationHandler
import io.jsonrpc4k.core.OutboundResourceLimits
import io.jsonrpc4k.transport.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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

internal fun requireNotReserved(method: String) {
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
