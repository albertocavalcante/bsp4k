package io.bsp4k.server

/**
 * The BSP lifecycle of a [BuildServer], declared in the order it moves through. It only moves forward,
 * except that a failed `build/initialize` returns to [Uninitialized]. It follows the protocol, not the
 * transport: [BuildServer.awaitTermination] reports when the session itself ends.
 */
public enum class BuildServerState {
    /**
     * Waiting for `build/initialize`. Other requests, `build/shutdown` included, fail with ServerNotInitialized
     * (-32002); notifications other than `build/exit` are dropped.
     */
    Uninitialized,

    /**
     * Handling `build/initialize`. From here on, another `build/initialize` fails with InvalidRequest (-32600);
     * other requests still fail with ServerNotInitialized and notifications other than `build/exit` are dropped.
     */
    Initializing,

    /**
     * The initialize result was produced; waiting for `build/initialized`. Application requests fail with
     * ServerNotInitialized, application notifications are dropped except control notifications such as
     * `run/readStdin` (which skip the ordering barrier and can overtake `build/initialized`), and
     * `build/shutdown` is accepted.
     */
    AwaitingInitialized,

    /** `build/initialized` arrived; application handlers are served. */
    Running,

    /**
     * `build/shutdown` was accepted. Requests, a repeated `build/shutdown` included, fail with InvalidRequest
     * (-32600); notifications other than `build/exit` are dropped.
     */
    ShutdownRequested,

    /** `build/exit` arrived; the session flushes queued messages and closes. */
    Exited,
}
