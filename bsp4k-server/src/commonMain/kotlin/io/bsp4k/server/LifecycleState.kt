package io.bsp4k.server

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/** The [BuildServerState] machine; every transition is atomic, so concurrent handlers cannot both win one. */
internal class LifecycleState {
    private val state = MutableStateFlow(BuildServerState.Uninitialized)

    val flow: StateFlow<BuildServerState> = state.asStateFlow()

    val current: BuildServerState
        get() = state.value

    /** Claims `build/initialize`; false when it already ran or is running. */
    fun beginInitialize(): Boolean = state.compareAndSet(BuildServerState.Uninitialized, BuildServerState.Initializing)

    /** Returns to [BuildServerState.Uninitialized] after a failed `build/initialize`. */
    fun abortInitialize() {
        state.compareAndSet(BuildServerState.Initializing, BuildServerState.Uninitialized)
    }

    /** Marks the initialize result as produced. */
    fun completeInitialize() {
        state.compareAndSet(BuildServerState.Initializing, BuildServerState.AwaitingInitialized)
    }

    /** Accepts the first `build/initialized` after the initialize result; false otherwise. */
    fun markInitialized(): Boolean = state.compareAndSet(BuildServerState.AwaitingInitialized, BuildServerState.Running)

    /** Accepts `build/shutdown` once the initialize result was produced; false before that or when repeated. */
    fun requestShutdown(): Boolean {
        while (true) {
            val observed = state.value
            if (observed != BuildServerState.AwaitingInitialized && observed != BuildServerState.Running) return false
            if (state.compareAndSet(observed, BuildServerState.ShutdownRequested)) return true
        }
    }

    /** Moves to [BuildServerState.Exited] and returns the state it left. */
    fun exit(): BuildServerState = state.getAndUpdate { BuildServerState.Exited }
}
