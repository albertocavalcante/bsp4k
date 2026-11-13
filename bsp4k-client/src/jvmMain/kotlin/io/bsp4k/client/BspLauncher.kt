package io.bsp4k.client

import io.bsp4k.protocol.BspConnectionDetails
import io.jsonrpc4k.transport.ProcessTransport
import io.jsonrpc4k.transport.StderrMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val DEFAULT_EXIT_TIMEOUT: Duration = 2.seconds

/**
 * Options for [launch].
 *
 * @property environment changes to the inherited environment of the server: a value sets a variable, null removes it.
 * @property stderr where the server's standard error goes.
 * @property exitTimeout how long [LaunchedBuildServer.close] waits for the server to exit after closing its stdin.
 * @property scope scope for the session; null creates a new supervised scope.
 * @property initialize when true, [launch] also completes `build/initialize` and `build/initialized`.
 * @property initializeTimeout timeout for `build/initialize` when [initialize] is true.
 */
public data class BspLaunchOptions(
    val environment: Map<String, String?> = emptyMap(),
    val stderr: StderrMode = StderrMode.Inherit,
    val exitTimeout: Duration = DEFAULT_EXIT_TIMEOUT,
    val scope: CoroutineScope? = null,
    val initialize: Boolean = false,
    val initializeTimeout: Duration = defaultRequestTimeout,
)

/** A server process started by [launch] and the connected [session]. The caller owns both and must [close] them. */
public class LaunchedBuildServer internal constructor(
    public val session: BuildClientSession,
    public val process: ProcessTransport,
) {
    /**
     * Stops the session immediately and closes the process transport, which ends the child. For an orderly stop
     * call [BuildClientSession.shutdown] first.
     */
    public suspend fun close() {
        try {
            session.close()
        } finally {
            withContext(NonCancellable) { process.close() }
        }
    }
}

/**
 * Starts the server described by [details] with its working directory set to [workspaceRoot] and returns a
 * connected session over the child's standard input and output.
 *
 * Unless [BspLaunchOptions.initialize] is set, the caller still has to call [BuildClientSession.initialize]; the
 * configured `rootUri` should match [workspaceRoot]. If starting the session or initializing fails, the child is
 * ended before the failure is rethrown.
 *
 * @throws io.jsonrpc4k.transport.TransportException when the process cannot be started.
 * @throws io.jsonrpc4k.transport.UnsupportedTransportException on platforms without process support.
 */
public suspend fun BuildClientConfig.launch(
    details: BspConnectionDetails,
    workspaceRoot: Path,
    options: BspLaunchOptions = BspLaunchOptions(),
): LaunchedBuildServer {
    val process =
        ProcessTransport.start(
            command = details.argv,
            environment = options.environment,
            workingDirectory = workspaceRoot.toString(),
            stderr = options.stderr,
            exitTimeout = options.exitTimeout,
        )
    var session: BuildClientSession? = null
    try {
        val started = start(process, options.scope ?: CoroutineScope(Dispatchers.Default + SupervisorJob()))
        session = started
        if (options.initialize) started.initialize(options.initializeTimeout)
        return LaunchedBuildServer(started, process)
    } catch (failure: Throwable) {
        withContext(NonCancellable) {
            session?.close()
            process.close()
        }
        throw failure
    }
}
