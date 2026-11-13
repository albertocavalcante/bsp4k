package io.bsp4k.client

import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Timeout applied to a request when the caller does not pass one. */
public val defaultRequestTimeout: Duration = 30.seconds

/** Lifecycle state of a [BuildClientSession]. */
public enum class SessionState {
    /** `build/initialize` has not been sent yet. */
    NEW,

    /** `build/initialize` is in flight. */
    INITIALIZING,

    /** The server accepted `build/initialize` and `build/initialized` was sent. */
    READY,

    /** `build/shutdown` was requested. */
    SHUTTING_DOWN,

    /** The session was closed or shut down. */
    CLOSED,

    /** `build/initialize` failed or returned an invalid result. */
    FAILED,
}

/** Thrown when an operation is not allowed in the current [SessionState]. */
public class SessionStateException(
    message: String,
    public val state: SessionState,
) : IllegalStateException(message)

/** Thrown when the server's `build/initialize` result is missing or invalid. */
public class InitializeResultException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/** Tracks the session state and rejects calls made outside [SessionState.READY]. */
internal class SessionGate {
    private val current = MutableStateFlow(SessionState.NEW)

    val state: SessionState get() = current.value

    fun requireReady(method: String) {
        val now = current.value
        if (now != SessionState.READY) {
            val hint = if (now == SessionState.NEW) " Call initialize() first." else ""
            throw SessionStateException("Cannot send $method: session state is $now.$hint", now)
        }
    }

    fun beginInitialize() {
        if (!current.compareAndSet(SessionState.NEW, SessionState.INITIALIZING)) {
            throw SessionStateException("initialize() cannot run: session state is ${current.value}", current.value)
        }
    }

    fun completeInitialize() {
        current.compareAndSet(SessionState.INITIALIZING, SessionState.READY)
    }

    fun failInitialize() {
        current.compareAndSet(SessionState.INITIALIZING, SessionState.FAILED)
    }

    fun beginShutdown() {
        while (true) {
            val now = current.value
            if (now != SessionState.NEW && now != SessionState.READY && now != SessionState.FAILED) {
                throw SessionStateException("Shutdown already started or session closed: state is $now", now)
            }
            if (current.compareAndSet(now, SessionState.SHUTTING_DOWN)) return
        }
    }

    fun close() {
        current.value = SessionState.CLOSED
    }

    /** Test seam: skips the initialize handshake for sessions driven by scripted peers. */
    fun assumeReady() {
        current.value = SessionState.READY
    }
}
