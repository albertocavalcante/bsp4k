import io.bsp4k.client.buildClient
import io.bsp4k.client.start
import io.bsp4k.server.buildServer
import io.bsp4k.server.start
import io.jsonrpc4k.transport.Transport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

fun main() =
    runBlocking {
        withTimeout(15_000) {
            val toClient = Channel<ByteArray>(Channel.UNLIMITED)
            val toServer = Channel<ByteArray>(Channel.UNLIMITED)
            val clientTransport = FixtureTransport(toClient, toServer)
            val serverTransport = FixtureTransport(toServer, toClient)
            val server = buildServer { serverInfo("fixture-server", "1") }.start(serverTransport, this)
            val client = buildClient { rootUri("file:///fixture") }.start(clientTransport, this)
            try {
                check(client.initialize().displayName == "fixture-server")
                client.shutdown()
                check(client.awaitTermination() == null)
                check(server.awaitTermination() == null)
            } finally {
                client.close()
                server.close()
                clientTransport.close()
                serverTransport.close()
            }
        }
    }

private class FixtureTransport(
    private val inbound: Channel<ByteArray>,
    private val outbound: Channel<ByteArray>,
) : Transport {
    override val incoming: Flow<ByteArray> = inbound.receiveAsFlow()
    override val isConnected: Boolean = true

    override suspend fun send(data: ByteArray) {
        outbound.send(data)
    }

    override suspend fun close() {
        inbound.close()
    }
}
