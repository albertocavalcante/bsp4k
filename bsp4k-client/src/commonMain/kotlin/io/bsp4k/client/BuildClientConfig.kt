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
 * Starts a build client session with the given configuration and [transport].
 * The supplied scope and transport remain caller-owned; closing a session does not close the transport.
 *
 * Dispatch is concurrent and bounded by [policy] (default: 16 concurrent handlers, 256 queued messages).
 * A notification is a barrier: earlier requests finish before it runs and later messages wait for it,
 * so server notifications are delivered to handlers in order. The client sets no
 * [BoundedDispatchPolicy.controlNotifications]: BSP defines no server-to-client request that a
 * notification could wait behind, and task and output notifications must stay ordered. Inbound
 * `$/cancelRequest` answers the request with RequestCancelled (-32800) and cancels its handler coroutine.
 *
 * When the transport's input ends, the session stops reading and lets the notifications and requests it
 * already admitted run, and writes any replies, before it terminates, so the last notifications a server
 * sends before closing its output still reach their handlers. [BoundedDispatchPolicy.drainTimeout]
 * (default 5 s) bounds that wait; after it, the remaining handlers are cancelled.
 * [inboundLimits] and [outboundLimits] are wire-size proxies, not a total heap bound; typed payload
 * conversion to JSON happens before outbound admission and is outside them. A transport collector
 * must not await reentrant sends on the same connection.
 */
public suspend fun BuildClientConfig.start(
    transport: Transport,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    policy: BoundedDispatchPolicy = SessionDefaults.policy,
    inboundLimits: InboundResourceLimits = SessionDefaults.inboundLimits,
    outboundLimits: OutboundResourceLimits = SessionDefaults.outboundLimits,
): BuildClientSession =
    startSession(
        transport,
        SessionLifecycle(scope, policy.drainTimeout) {
            Connection(policy, inboundLimits, outboundLimits, it)
        },
    )

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
