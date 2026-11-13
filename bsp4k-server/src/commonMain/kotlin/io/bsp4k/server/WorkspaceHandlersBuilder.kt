package io.bsp4k.server

import io.bsp4k.protocol.BspMethods
import io.bsp4k.protocol.WorkspaceBuildTargetsResult

/**
 * Registers handlers for the core `workspace/` requests. The Cargo extension's `workspace/` requests live in
 * [CargoHandlersBuilder].
 */
@BspServerDsl
public class WorkspaceHandlersBuilder internal constructor(
    private val registry: HandlerRegistry,
) {
    /** Handles `workspace/buildTargets`. */
    public fun buildTargets(handler: suspend BuildServerContext.() -> WorkspaceBuildTargetsResult) {
        registry.handleParamless(BspMethods.WORKSPACE_BUILD_TARGETS, handler)
    }

    /**
     * Handles `workspace/reload`; requires `canReload`. When it throws, the client receives the error and BSP
     * expects other requests to keep answering from the last good state.
     */
    public fun reload(handler: suspend BuildServerContext.() -> Unit) {
        registry.registerRequest(BspMethods.WORKSPACE_RELOAD) {
            handler(this)
            null
        }
    }
}
