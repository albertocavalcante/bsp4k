package io.bsp4k.client

import io.jsonrpc4k.core.Connection
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
) {
    private data class End(
        val cause: Throwable?,
    )

    private val job = SupervisorJob(parent.coroutineContext[Job])
    private val scope = CoroutineScope(parent.coroutineContext + job)
    val connection = Connection(scope = scope)
    private val end = MutableStateFlow<End?>(null)
    private val terminated = CompletableDeferred<Throwable?>()

    init {
        job.invokeOnCompletion {
            connection.close()
            terminated.complete(end.value?.cause)
        }
    }

    fun start(transport: Transport) {
        scope.launch {
            try {
                transport.incoming.collect { connection.receive(it) }
                finish(null)
            } catch (cancelled: CancellationException) {
                if (currentCoroutineContext().isActive) finish(cancelled) else throw cancelled
            } catch (failure: Throwable) {
                finish(failure)
                if (failure !is Exception) throw failure
            }
        }
        scope.launch {
            try {
                connection.outgoing.collect { transport.send(it) }
                finish(null)
            } catch (cancelled: CancellationException) {
                if (currentCoroutineContext().isActive) finish(cancelled) else throw cancelled
            } catch (failure: Throwable) {
                finish(failure)
                if (failure !is Exception) throw failure
            }
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
        if (end.compareAndSet(null, End(cause))) {
            connection.close()
            job.cancel()
        }
    }
}
