package io.bsp4k.client

import io.jsonrpc4k.core.Connection
import io.jsonrpc4k.core.ConnectionState
import io.jsonrpc4k.transport.Transport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

/** Owns session jobs; the supplied scope and transport remain caller-owned. */
internal class SessionLifecycle(
    parent: CoroutineScope,
    private val drainTimeout: Duration,
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
    private val exiting = MutableStateFlow(false)

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
        scope.launch {
            runPump {
                transport.incoming.collect { connection.receive(it) }
                drainAfterEndOfInput()
            }
        }
        scope.launch { runPump { connection.outgoing.collect { transport.send(it) } } }
    }

    /**
     * The transport delivered its last inbound byte. Requests already admitted still get their
     * responses written before the session finishes: the core stops admission and waits for admitted
     * work (bounded by [io.jsonrpc4k.core.BoundedDispatchPolicy.drainTimeout], after which it cancels
     * what remains and answers it with RequestCancelled), then the writer flushes what was queued.
     * A peer that closes its output right after its last request therefore still receives the reply.
     * [drainTimeout] also bounds the flush, so a writer stuck on a peer that is gone cannot keep the
     * session alive; the session then finishes and discards what is unwritten. Once [exit] has begun,
     * EOF is the server's expected answer to `build/exit` and ends the session at once, as [exit]
     * documents, without waiting for the exit frame's write.
     */
    private suspend fun drainAfterEndOfInput() {
        if (exiting.value) return
        connection.endInput()
        withTimeoutOrNull(drainTimeout) { connection.flushOutgoing() }
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

    /**
     * Sends the final frame and waits until the writer returns it. If the session ends first, for
     * example because the peer closes after reading that frame, the session outcome is the result.
     */
    suspend fun exit(send: suspend () -> Unit) {
        exiting.value = true
        try {
            send()
            flushOutgoing()
        } catch (failure: Exception) {
            // Session teardown cancels the core's queue and barriers, not this caller.
            currentCoroutineContext().ensureActive()
            if (end.value == null && connection.state.value is ConnectionState.Open) throw failure
            finish(null)
            resolveEnd().cause?.let { throw it }
        }
    }

    private suspend fun flushOutgoing() {
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
