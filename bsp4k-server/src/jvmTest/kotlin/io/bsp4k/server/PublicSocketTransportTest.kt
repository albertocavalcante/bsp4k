package io.bsp4k.server

import io.bsp4k.protocol.WorkspaceBuildTargetsResult
import io.jsonrpc4k.transport.SocketTransport
import io.jsonrpc4k.transport.Transport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PublicSocketTransportTest {
    @Test
    fun `public server handles framed lifecycle over loopback without owning socket`() =
        runBlocking {
            val parent = SupervisorJob()
            val sibling = Job(parent)
            val scope = CoroutineScope(Dispatchers.Default + parent)
            ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { listener ->
                listener.soTimeout = 5_000
                val accepted = async(Dispatchers.IO) { listener.accept() }
                val transport = ObservedSocket(SocketTransport.connect("127.0.0.1", listener.localPort))
                var started: BuildServer? = null
                try {
                    val peer = withTimeout(8_000) { accepted.await() }
                    peer.use {
                        peer.soTimeout = 5_000
                        val session =
                            buildServer {
                                serverInfo("socket-server", "1")
                                workspace { buildTargets { WorkspaceBuildTargetsResult(emptyList()) } }
                            }.start(transport, scope)
                        started = session
                        val input = peer.getInputStream()
                        val output = peer.getOutputStream()
                        withContext(Dispatchers.IO) {
                            writeFrame(
                                output,
                                """{"jsonrpc":"2.0","id":1,"method":"build/initialize","params":{"displayName":"raw","version":"1","bspVersion":"2.1.0","rootUri":"file:///fixture","capabilities":{"languageIds":["groovy"]}}}""",
                            )
                            val init = Json.parseToJsonElement(readFrame(input)).jsonObject
                            assertEquals("1", init["id"]!!.jsonPrimitive.content)
                            assertEquals("socket-server", init["result"]!!.jsonObject["displayName"]!!.jsonPrimitive.content)
                            writeFrame(output, """{"jsonrpc":"2.0","method":"build/initialized"}""")
                            writeFrame(output, """{"jsonrpc":"2.0","id":2,"method":"workspace/buildTargets"}""")
                            val targets = Json.parseToJsonElement(readFrame(input)).jsonObject
                            assertEquals(0, targets["result"]!!.jsonObject["targets"]!!.jsonArray.size)
                            writeFrame(output, """{"jsonrpc":"2.0","id":3,"method":"build/shutdown"}""")
                            val shutdown = Json.parseToJsonElement(readFrame(input)).jsonObject
                            assertEquals("3", shutdown["id"]!!.jsonPrimitive.content)
                            assertTrue("result" in shutdown)
                            assertEquals(JsonNull, shutdown["result"])
                            writeFrame(output, """{"jsonrpc":"2.0","method":"build/exit"}""")
                        }
                        withTimeout(8_000) { transport.exitObserved.await() }
                        withContext(Dispatchers.IO) { peer.shutdownOutput() }
                        assertEquals(null, withTimeout(8_000) { session.awaitTermination() })
                        withTimeout(8_000) { transport.readerFinished.await() }
                        assertEquals(0, transport.closeCalls.get())
                        assertTrue(parent.isActive)
                        assertTrue(sibling.isActive)
                    }
                } finally {
                    started?.close()
                    transport.close()
                    parent.cancel()
                }
            }
        }
}

private class ObservedSocket(
    private val delegate: Transport,
) : Transport {
    val closeCalls = AtomicInteger()
    val readerFinished = CompletableDeferred<Unit>()
    val exitObserved = CompletableDeferred<Unit>()
    private val received = ByteArrayOutputStream()
    override val incoming: Flow<ByteArray> =
        delegate.incoming
            .onEach { bytes ->
                received.write(bytes)
                if (received.toString(StandardCharsets.UTF_8).contains("\"method\":\"build/exit\"")) exitObserved.complete(Unit)
            }.onCompletion { readerFinished.complete(Unit) }
    override val isConnected: Boolean get() = delegate.isConnected

    override suspend fun send(data: ByteArray) = delegate.send(data)

    override suspend fun close() {
        closeCalls.incrementAndGet()
        delegate.close()
    }
}

private fun writeFrame(
    output: OutputStream,
    json: String,
) {
    val body = json.toByteArray(StandardCharsets.UTF_8)
    output.write("Content-Length: ${body.size}\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
    output.write(body)
    output.flush()
}

private fun readFrame(input: InputStream): String {
    val header = ByteArrayOutputStream()
    val delimiter = byteArrayOf(13, 10, 13, 10)
    var matched = 0
    while (matched < delimiter.size) {
        val value = input.read()
        check(value >= 0) { "EOF before frame header" }
        header.write(value)
        check(header.size() <= 16_384)
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
        requireNotNull(Regex("(?i)content-length:\\s*(\\d+)").find(header.toString(StandardCharsets.US_ASCII)))
            .groupValues[1]
            .toInt()
    check(length in 0..65_536)
    val body = input.readNBytes(length)
    check(body.size == length)
    return body.toString(StandardCharsets.UTF_8)
}
