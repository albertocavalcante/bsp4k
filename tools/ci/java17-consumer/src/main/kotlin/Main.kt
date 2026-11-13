import io.bsp4k.client.buildClient
import io.bsp4k.client.start
import io.bsp4k.server.buildServer
import io.bsp4k.server.start
import io.jsonrpc4k.core.BoundedDispatchPolicy
import io.jsonrpc4k.core.InboundResourceLimits
import io.jsonrpc4k.core.OutboundResourceLimits
import io.jsonrpc4k.transport.Transport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

fun main() =
    runBlocking {
        withTimeout(15_000) {
            repeat(2) { iteration ->
                val toClient = Channel<ByteArray>(Channel.UNLIMITED)
                val toServer = Channel<ByteArray>(Channel.UNLIMITED)
                val clientTransport = FixtureTransport(toClient, toServer)
                val serverTransport = FixtureTransport(toServer, toClient)
                val policy = BoundedDispatchPolicy(2, 8)
                val inbound = InboundResourceLimits(4096, 32, 8192)
                val outbound = OutboundResourceLimits(4096, 8192, 8, 8, 4096, 2, 1024, 32)
                val serverConfig = buildServer { serverInfo("fixture-server", "1") }
                val clientConfig = buildClient { rootUri("file:///fixture") }
                val server =
                    if (iteration == 0) {
                        serverConfig.start(serverTransport, this)
                    } else {
                        serverConfig.start(serverTransport, this, policy, inbound, outbound)
                    }
                val client =
                    if (iteration == 0) {
                        clientConfig.start(clientTransport, this)
                    } else {
                        clientConfig.start(clientTransport, this, policy, inbound, outbound)
                    }
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
