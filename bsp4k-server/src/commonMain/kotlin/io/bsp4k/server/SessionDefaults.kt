package io.bsp4k.server

import io.bsp4k.protocol.BspMethods
import io.jsonrpc4k.core.BoundedDispatchPolicy
import io.jsonrpc4k.core.InboundResourceLimits
import io.jsonrpc4k.core.OutboundResourceLimits

private const val MAX_CONCURRENT_REQUESTS = 16
private const val MAX_QUEUED_MESSAGES = 256
private const val MAX_INBOUND_FRAME_BYTES = 16 * 1024 * 1024
private const val MAX_INBOUND_NESTING = 128
private const val MAX_INBOUND_APPLICATION_BYTES = 64 * 1024 * 1024
private const val MAX_OUTBOUND_FRAME_BYTES = 16 * 1024 * 1024
private const val MAX_OUTBOUND_RETAINED_BYTES = 64 * 1024 * 1024
private const val MAX_OUTBOUND_FRAMES = 256
private const val MAX_OUTBOUND_PRODUCERS = 256
private const val CONTROL_RESERVE_BYTES = 64 * 1024
private const val CONTROL_RESERVE_FRAMES = 16
private const val MAX_OUTBOUND_NODES = 1_000_000
private const val MAX_OUTBOUND_NESTING = 128

/** Defaults for a session's dispatch policy and resource limits; callers override them per `start`. */
internal object SessionDefaults {
    /**
     * Client-to-server notifications that bypass the ordering barrier, so that they reach request
     * handlers that are still running. Only `run/readStdin` qualifies: its input belongs to a running
     * `buildTarget/run`. `build/initialized` and `build/exit` must stay barriers (initialization precedes
     * later requests; exit follows the requests before it, such as `build/shutdown`), and
     * `$/cancelRequest` already bypasses the queue in jsonrpc4k.
     */
    val controlNotifications: Set<String> = setOf(BspMethods.RUN_READ_STDIN)

    /**
     * Up to 16 concurrent request handlers, 256 queued messages, jsonrpc4k's default drain timeout and
     * the [controlNotifications] lane.
     */
    val policy: BoundedDispatchPolicy =
        BoundedDispatchPolicy(MAX_CONCURRENT_REQUESTS, MAX_QUEUED_MESSAGES, controlNotifications = controlNotifications)

    /** The caller's [policy] with the server's [controlNotifications] added to the ones it lists. */
    fun withControlNotifications(policy: BoundedDispatchPolicy): BoundedDispatchPolicy =
        policy.copy(controlNotifications = policy.controlNotifications + controlNotifications)

    /** 16 MiB frames, 128 levels of JSON nesting and 64 MiB of retained application bodies. */
    val inboundLimits: InboundResourceLimits =
        InboundResourceLimits(MAX_INBOUND_FRAME_BYTES, MAX_INBOUND_NESTING, MAX_INBOUND_APPLICATION_BYTES)

    /** 16 MiB frames and 64 MiB retained, with a small reserve so control frames are never starved. */
    val outboundLimits: OutboundResourceLimits =
        OutboundResourceLimits(
            MAX_OUTBOUND_FRAME_BYTES,
            MAX_OUTBOUND_RETAINED_BYTES,
            MAX_OUTBOUND_FRAMES,
            MAX_OUTBOUND_PRODUCERS,
            CONTROL_RESERVE_BYTES,
            CONTROL_RESERVE_FRAMES,
            MAX_OUTBOUND_NODES,
            MAX_OUTBOUND_NESTING,
        )
}
