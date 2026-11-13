package io.bsp4k.client

import io.jsonrpc4k.core.NotificationHandler
import io.jsonrpc4k.transport.SocketTransport
import io.jsonrpc4k.transport.StreamTransport
import io.jsonrpc4k.transport.Transport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PublicTransportTest {
    @Test
    fun `public client completes lifecycle over loopback socket without owning transport`() =
        runBlocking {
            val parent = SupervisorJob()
            val sibling = Job(parent)
            val scope = CoroutineScope(Dispatchers.Default + parent)
            ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { listener ->
                listener.soTimeout = 5_000
                val peer =
                    async(Dispatchers.IO) {
                        listener.accept().use { socket ->
                            socket.soTimeout = 5_000
                            servePeer(socket.getInputStream(), socket.getOutputStream())
                        }
                    }
                // Pin the interleaving that flaked in CI: the peer's EOF ends the session before the exit write returns.
                val transport = ObservedTransport(SocketTransport.connect("127.0.0.1", listener.localPort), holdExitSend = true)
                var started: BuildClientSession? = null
                try {
                    val session = buildClient { rootUri("file:///fixture") }.start(transport, scope)
                    started = session
                    assertEquals("socket-π", withTimeout(8_000) { session.initialize() }.displayName)
                    assertEquals(emptyList(), withTimeout(8_000) { session.server.workspaceBuildTargets() }?.targets)
                    withTimeout(8_000) { session.shutdown() }
                    withTimeout(8_000) { peer.await() }
                    assertEquals(null, withTimeout(8_000) { session.awaitTermination() })
                    withTimeout(8_000) { transport.readerFinished.await() }
                    assertEquals(0, transport.closeCalls.get())
                    assertTrue(parent.isActive)
                    assertTrue(sibling.isActive)
                } finally {
                    started?.close()
                    transport.close()
                    parent.cancel()
                }
            }
        }

    @Test
    fun `owner closes a borrowed socket after session cancellation with a request pending`() =
        runBlocking {
            val parent = SupervisorJob()
            val sibling = Job(parent)
            val scope = CoroutineScope(Dispatchers.Default + parent)
            val requestSeen = CompletableDeferred<Unit>()
            val releasePeer = CompletableDeferred<Unit>()
            ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { listener ->
                listener.soTimeout = 5_000
                val peer =
                    async(Dispatchers.IO) {
                        listener.accept().use { socket ->
                            socket.soTimeout = 5_000
                            val input = socket.getInputStream()
                            val output = socket.getOutputStream()
                            val initialize = readFrame(input)
                            reply(output, initialize, """{"displayName":"socket-π","version":"1","bspVersion":"2.1.0","capabilities":{}}""")
                            assertTrue(readFrame(input).contains("\"method\":\"build/initialized\""))
                            assertTrue(readFrame(input).contains("\"method\":\"workspace/buildTargets\""))
                            requestSeen.complete(Unit)
                            releasePeer.await()
                        }
                    }
                val transport = ObservedTransport(SocketTransport.connect("127.0.0.1", listener.localPort))
                var started: BuildClientSession? = null
                try {
                    val session = buildClient { rootUri("file:///fixture") }.start(transport, scope)
                    started = session
                    withTimeout(8_000) { session.initialize() }
                    val pending = async { runCatching { session.server.workspaceBuildTargets() } }
                    withTimeout(8_000) { requestSeen.await() }
                    session.close()
                    assertTrue(withTimeout(8_000) { pending.await() }.isFailure)
                    assertEquals(0, transport.closeCalls.get())
                    val ownerClose = async(Dispatchers.IO) { transport.close() }
                    try {
                        withTimeout(8_000) { ownerClose.await() }
                        assertEquals(null, withTimeout(8_000) { session.awaitTermination() })
                        withTimeout(8_000) { transport.readerFinished.await() }
                        assertTrue(parent.isActive)
                        assertTrue(sibling.isActive)
                    } finally {
                        releasePeer.complete(Unit)
                    }
                    withTimeout(8_000) { peer.await() }
                } finally {
                    releasePeer.complete(Unit)
                    started?.close()
                    transport.close()
                    parent.cancel()
                }
            }
        }

    @Test
    fun `socket reset fails public session and cancels a pending request`() =
        runBlocking {
            val parent = SupervisorJob()
            val sibling = Job(parent)
            val scope = CoroutineScope(Dispatchers.Default + parent)
            ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { listener ->
                listener.soTimeout = 5_000
                val peer =
                    async(Dispatchers.IO) {
                        listener.accept().use { socket ->
                            socket.soTimeout = 5_000
                            val input = socket.getInputStream()
                            val initialize = readFrame(input)
                            reply(
                                socket.getOutputStream(),
                                initialize,
                                """{"displayName":"socket-π","version":"1","bspVersion":"2.1.0","capabilities":{}}""",
                            )
                            assertTrue(readFrame(input).contains("\"method\":\"build/initialized\""))
                            assertTrue(readFrame(input).contains("\"method\":\"workspace/buildTargets\""))
                            socket.setSoLinger(true, 0)
                        }
                    }
                val transport = ObservedTransport(SocketTransport.connect("127.0.0.1", listener.localPort))
                var started: BuildClientSession? = null
                try {
                    val session = buildClient { rootUri("file:///fixture") }.start(transport, scope)
                    started = session
                    withTimeout(8_000) { session.initialize() }
                    val pending = async { runCatching { session.server.workspaceBuildTargets() } }
                    withTimeout(8_000) { peer.await() }
                    assertTrue(withTimeout(8_000) { pending.await() }.isFailure)
                    val failure = assertNotNull(withTimeout(8_000) { session.awaitTermination() })
                    assertTrue(generateSequence(failure) { it.cause }.take(8).any { it is SocketException })
                    withTimeout(8_000) { transport.readerFinished.await() }
                    assertEquals(0, transport.closeCalls.get())
                    assertTrue(parent.isActive)
                    assertTrue(sibling.isActive)
                } finally {
                    started?.close()
                    transport.close()
                    parent.cancel()
                }
            }
        }

    @Test
    fun `public client uses a child process stream for initialization and graceful exit`() =
        runBlocking {
            withChildPeer { process, transport, _ ->
                val parent = SupervisorJob()
                val sibling = Job(parent)
                val scope = CoroutineScope(Dispatchers.Default + parent)
                var started: BuildClientSession? = null
                try {
                    val session = buildClient { rootUri("file:///fixture") }.start(transport, scope)
                    started = session
                    assertEquals("child-π", withTimeout(8_000) { session.initialize() }.displayName)
                    assertEquals(emptyList(), withTimeout(8_000) { session.server.workspaceBuildTargets() }?.targets)
                    withTimeout(8_000) { session.shutdown() }
                    assertTrue(withContext(Dispatchers.IO) { process.waitFor(5, TimeUnit.SECONDS) })
                    assertEquals(0, process.exitValue())
                    assertEquals(null, withTimeout(8_000) { session.awaitTermination() })
                    withTimeout(8_000) { transport.readerFinished.await() }
                    assertEquals(0, transport.closeCalls.get())
                    assertTrue(parent.isActive)
                    assertTrue(sibling.isActive)
                } finally {
                    started?.close()
                    parent.cancel()
                }
            }
        }

    @Test
    fun `child EOF terminates public session and cancels an outstanding request`() =
        runBlocking {
            withChildPeer("eof") { process, transport, _ ->
                val parent = SupervisorJob()
                val sibling = Job(parent)
                val scope = CoroutineScope(Dispatchers.Default + parent)
                var started: BuildClientSession? = null
                try {
                    val session = buildClient { rootUri("file:///fixture") }.start(transport, scope)
                    started = session
                    withTimeout(8_000) { session.initialize() }
                    val pending = async { runCatching { session.server.workspaceBuildTargets() } }
                    assertTrue(withTimeout(8_000) { pending.await() }.isFailure)
                    assertEquals(null, withTimeout(8_000) { session.awaitTermination() })
                    withTimeout(8_000) { transport.readerFinished.await() }
                    assertTrue(withContext(Dispatchers.IO) { process.waitFor(5, TimeUnit.SECONDS) })
                    assertEquals(0, process.exitValue())
                    assertEquals(0, transport.closeCalls.get())
                    assertTrue(parent.isActive)
                    assertTrue(sibling.isActive)
                } finally {
                    started?.close()
                    parent.cancel()
                }
            }
        }

    @Test
    fun `owner closes child streams while peer keeps stdout open`() =
        runBlocking {
            withChildPeer("hold") { _, transport, child ->
                val parent = SupervisorJob()
                val sibling = Job(parent)
                val scope = CoroutineScope(Dispatchers.Default + parent)
                val ready = CompletableDeferred<Unit>()
                var started: BuildClientSession? = null
                var closer: Thread? = null
                try {
                    val session =
                        buildClient {
                            rootUri("file:///fixture")
                            onNotification("fixture/ready", NotificationHandler { ready.complete(Unit) })
                        }.start(transport, scope)
                    started = session
                    withTimeout(8_000) { session.initialize() }
                    val pending = async { runCatching { session.server.workspaceBuildTargets() } }
                    withTimeout(8_000) { ready.await() }
                    session.close()
                    assertTrue(withTimeout(8_000) { pending.await() }.isFailure)
                    assertEquals(0, transport.closeCalls.get())
                    val closeResult = CompletableFuture<Unit>()
                    closer =
                        Thread({
                            try {
                                runBlocking { transport.close() }
                                closeResult.complete(Unit)
                            } catch (failure: Throwable) {
                                closeResult.completeExceptionally(failure)
                            }
                        }, "bsp-live-child-close").apply {
                            isDaemon = true
                            start()
                        }
                    withContext(Dispatchers.IO) { closeResult.get(5, TimeUnit.SECONDS) }
                    assertEquals(null, withTimeout(8_000) { session.awaitTermination() })
                    withTimeout(8_000) { transport.readerFinished.await() }
                    assertTrue(parent.isActive)
                    assertTrue(sibling.isActive)
                } finally {
                    started?.close()
                    try {
                        withContext(NonCancellable + Dispatchers.IO) { child.destroy() }
                    } finally {
                        try {
                            closer?.join(5_000)
                            check(closer?.isAlive != true) { "Live-child transport close remained blocked after child exit" }
                        } finally {
                            parent.cancel()
                        }
                    }
                }
            }
        }

    private suspend fun withChildPeer(
        mode: String = "normal",
        block: suspend (Process, ObservedTransport, ChildCleanup) -> Unit,
    ) {
        val directory = Files.createTempDirectory("bsp-child-π-")
        val source = directory.resolve("BspFixturePeer.java")
        val errors = directory.resolve("stderr.log")
        var process: Process? = null
        var transport: ObservedTransport? = null
        var child: ChildCleanup? = null
        try {
            val resource = checkNotNull(javaClass.classLoader.getResourceAsStream("BspFixturePeer.java"))
            resource.use { Files.copy(it, source) }
            check(System.getProperty("java.specification.version") == "17") { "Child fixture requires Java 17" }
            val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
            process =
                ProcessBuilder(java, "-Xmx128m", "-Dfile.encoding=UTF-8", "--source", "17", source.toString(), mode)
                    .redirectError(errors.toFile())
                    .start()
            child = ChildCleanup(process)
            transport = ObservedTransport(StreamTransport(process.inputStream, process.outputStream))
            block(process, transport, child)
        } catch (failure: Throwable) {
            val detail = if (Files.exists(errors)) Files.readString(errors) else ""
            throw AssertionError("Child peer failed: $detail", failure)
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                try {
                    // A live child can hold the process-pipe read lock. Never wait on transport
                    // close before proving our child exited, even when the test was cancelled.
                    if (child?.failed != true) {
                        child?.destroy()
                        transport?.let { closeChildTransport(it) }
                    }
                } finally {
                    Files.deleteIfExists(source)
                    Files.deleteIfExists(errors)
                    Files.deleteIfExists(directory)
                }
            }
        }
    }

    private class ChildCleanup(
        private val process: Process,
    ) {
        private var attempted = false
        private var complete = false
        val failed: Boolean get() = attempted && !complete

        fun destroy() {
            if (complete) return
            check(!attempted) { "Child termination already timed out or failed; transport close skipped" }
            attempted = true
            val result = CompletableFuture<Unit>()
            Thread({
                try {
                    process.destroyForcibly()
                    check(process.waitFor(5, TimeUnit.SECONDS)) { "Child did not exit after forced termination" }
                    result.complete(Unit)
                } catch (failure: Throwable) {
                    result.completeExceptionally(failure)
                }
            }, "bsp-child-destroy").apply {
                isDaemon = true
                start()
            }
            try {
                result.get(5, TimeUnit.SECONDS)
                complete = true
            } catch (failure: TimeoutException) {
                throw AssertionError("Child termination blocked; transport close skipped", failure)
            }
        }
    }

    private fun closeChildTransport(transport: Transport) {
        val closed = CompletableFuture<Unit>()
        val closer =
            Thread({
                try {
                    runBlocking { transport.close() }
                    closed.complete(Unit)
                } catch (failure: Throwable) {
                    closed.completeExceptionally(failure)
                }
            }, "bsp-child-transport-close")
        closer.isDaemon = true
        closer.start()
        try {
            closed.get(5, TimeUnit.SECONDS)
            closer.join(1_000)
            check(!closer.isAlive) { "Child transport closer did not stop" }
        } catch (failure: TimeoutException) {
            throw AssertionError("Child transport close blocked after the child exited", failure)
        }
    }
}

private class ObservedTransport(
    private val delegate: Transport,
    private val holdExitSend: Boolean = false,
) : Transport {
    val closeCalls = AtomicInteger()
    val readerFinished = CompletableDeferred<Unit>()
    override val incoming: Flow<ByteArray> = delegate.incoming.onCompletion { readerFinished.complete(Unit) }
    override val isConnected: Boolean get() = delegate.isConnected

    override suspend fun send(data: ByteArray) {
        delegate.send(data)
        // The exit frame is on the wire, but this send returns only when session teardown cancels it.
        if (holdExitSend && data.decodeToString().contains("\"method\":\"build/exit\"")) awaitCancellation()
    }

    override suspend fun close() {
        closeCalls.incrementAndGet()
        delegate.close()
    }
}

private fun readFrame(input: InputStream): String {
    val header = ByteArrayOutputStream()
    val delimiter = byteArrayOf(13, 10, 13, 10)
    var matched = 0
    while (matched < delimiter.size) {
        val value = input.read()
        check(value >= 0) { "EOF before frame header" }
        header.write(value)
        check(header.size() <= 16_384) { "Frame header too large" }
        matched =
            if (value == delimiter[matched].toInt()) {
                matched + 1
            } else if (value == 13) {
                1
            } else {
                0
            }
    }
    val length =
        Regex("(?i)content-length:\\s*(\\d+)")
            .find(header.toString(StandardCharsets.US_ASCII))
            ?.groupValues
            ?.get(1)
            ?.toInt()
    val count = assertNotNull(length)
    check(count in 0..65_536)
    val body = input.readNBytes(count)
    assertEquals(count, body.size)
    return body.toString(StandardCharsets.UTF_8)
}

private fun reply(
    output: OutputStream,
    request: String,
    result: String,
) {
    val id =
        Json
            .parseToJsonElement(request)
            .jsonObject["id"]!!
            .jsonPrimitive.content
    val body = """{"jsonrpc":"2.0","id":$id,"result":$result}""".toByteArray(StandardCharsets.UTF_8)
    output.write("Content-Length: ${body.size}\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
    output.write(body)
    output.flush()
}

private fun servePeer(
    input: InputStream,
    output: OutputStream,
) {
    val initialize = readFrame(input)
    assertTrue(initialize.contains("\"method\":\"build/initialize\""))
    assertTrue(initialize.contains("file:///fixture"))
    reply(output, initialize, """{"displayName":"socket-π","version":"1","bspVersion":"2.1.0","capabilities":{}}""")
    assertTrue(readFrame(input).contains("\"method\":\"build/initialized\""))
    val targets = readFrame(input)
    assertTrue(targets.contains("\"method\":\"workspace/buildTargets\""))
    reply(output, targets, """{"targets":[]}""")
    val shutdown = readFrame(input)
    assertTrue(shutdown.contains("\"method\":\"build/shutdown\""))
    reply(output, shutdown, "null")
    assertTrue(readFrame(input).contains("\"method\":\"build/exit\""))
}
