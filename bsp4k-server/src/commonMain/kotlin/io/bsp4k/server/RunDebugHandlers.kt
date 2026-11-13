package io.bsp4k.server

import io.bsp4k.protocol.BspMethods
import io.bsp4k.protocol.DebugSessionAddress
import io.bsp4k.protocol.DebugSessionParams
import io.bsp4k.protocol.ReadParams

/** Registers the handler for `debugSession/start`. */
@BspServerDsl
public class DebugSessionHandlersBuilder internal constructor(
    private val registry: HandlerRegistry,
) {
    /** Handles `debugSession/start`: launch a DAP server and return its address. Requires `debugProvider`. */
    public fun start(handler: suspend BuildServerContext.(DebugSessionParams) -> DebugSessionAddress) {
        registry.handleRequest(BspMethods.DEBUG_SESSION_START, handler)
    }
}

/** Registers handlers for the `run/` notifications a client sends. */
@BspServerDsl
public class RunHandlersBuilder internal constructor(
    private val registry: HandlerRegistry,
) {
    /**
     * Handles `run/readStdin` (unstable in BSP 2.2): input for a process started by `buildTarget/run`.
     *
     * Limitation: jsonrpc4k runs a notification only after the requests received before it have finished, in
     * both session modes. Until that changes, input sent while a `buildTarget/run` request is in progress
     * reaches this handler only after that request has finished.
     */
    public fun readStdin(handler: suspend BuildServerContext.(ReadParams) -> Unit) {
        registry.handleNotification(BspMethods.RUN_READ_STDIN, handler)
    }
}
