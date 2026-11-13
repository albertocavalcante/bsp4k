package io.bsp4k.server

import io.jsonrpc4k.core.Connection
import io.jsonrpc4k.core.ConnectionState
import io.jsonrpc4k.transport.Transport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** Owns session jobs; the supplied scope and transport remain caller-owned. */
internal class SessionLifecycle(
    parent: CoroutineScope,
    connectionFactory: (CoroutineScope) -> Connection,
) {
    private data class End(
        val cause: Throwable?,
        val resolved: Boolean,
    )

    private val job = SupervisorJob(parent.coroutineContext[Job])
    private val scope = CoroutineScope(parent.coroutineContext + job)
    val connection = connectionFactory(scope)
    private val end = MutableStateFlow<End?>(null)
    private val terminated = CompletableDeferred<Throwable?>()

    init {
        job.invokeOnCompletion {
            end.compareAndSet(null, End(null, resolved = false))
            terminated.complete(resolveEnd().cause)
        }
    }

    fun start(transport: Transport) {
        scope.launch {
            connection.state.collect { state ->
                if (state is ConnectionState.Failed) finish(state.cause)
            }
        }
        scope.launch { runPump { transport.incoming.collect { connection.receive(it) } } }
        scope.launch { runPump { connection.outgoing.collect { transport.send(it) } } }
    }

    private suspend fun runPump(block: suspend () -> Unit) {
        try {
            block()
            finish(null)
        } catch (cancelled: CancellationException) {
            if (currentCoroutineContext().isActive) finish(cancelled) else throw cancelled
        } catch (failure: Exception) {
            finish(failure)
        } catch (fatal: Throwable) {
            finish(fatal)
            throw fatal
        }
    }

    fun close() = finish(null)

    fun fail(cause: Throwable) = finish(cause)

    suspend fun awaitTermination(): Throwable? = terminated.await()

    suspend fun flushOutgoing() {
        try {
            connection.flushOutgoing()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // A failed send can wake the flush before the writer catches that same failure.
            if (job.isActive) fail(failure.cause ?: failure)
            throw failure
        }
    }

    private fun finish(cause: Throwable?) {
        // Reserve the session outcome before close wakes other pumps or callers. Closing the
        // core may materialize an earlier core terminal claim that is not yet in its StateFlow.
        if (end.compareAndSet(null, End(cause, resolved = false))) {
            try {
                resolveEnd()
            } finally {
                job.cancel()
            }
        }
    }

    private fun resolveEnd(): End {
        connection.close()
        while (true) {
            val current = end.value
            if (current == null) {
                end.compareAndSet(null, End(null, resolved = false))
            } else if (current.resolved) {
                return current
            } else {
                val coreFailure = (connection.state.value as? ConnectionState.Failed)?.cause
                val resolved = End(coreFailure ?: current.cause, resolved = true)
                if (end.compareAndSet(current, resolved)) return resolved
            }
        }
    }
}
