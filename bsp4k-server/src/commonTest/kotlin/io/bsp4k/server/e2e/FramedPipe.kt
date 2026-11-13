package io.bsp4k.server.e2e

import io.jsonrpc4k.transport.Transport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

private const val CHUNK_SIZE = 13

/**
 * One end of an in-memory pipe. Every frame written with [send] reaches the peer's [incoming] flow cut into
 * [CHUNK_SIZE]-byte pieces, so the receiving session must reassemble `Content-Length` frames from arbitrary
 * chunk boundaries, as it must on a real stream. [close] ends the peer's input like a closed socket.
 */
internal class PipeEnd(
    private val inbound: Channel<ByteArray>,
    private val outbound: Channel<ByteArray>,
) : Transport {
    override val incoming: Flow<ByteArray> = inbound.receiveAsFlow()
    override val isConnected: Boolean get() = !outbound.isClosedForSend

    override suspend fun send(data: ByteArray) {
        var offset = 0
        while (offset < data.size) {
            val end = minOf(offset + CHUNK_SIZE, data.size)
            outbound.send(data.copyOfRange(offset, end))
            offset = end
        }
    }

    override suspend fun close() {
        outbound.close()
    }
}

/** A connected pair of transports: what [client] sends arrives at [server] and the other way round. */
internal class FramedPipe {
    private val towardServer = Channel<ByteArray>(Channel.UNLIMITED)
    private val towardClient = Channel<ByteArray>(Channel.UNLIMITED)

    /** The transport handed to the build client session. */
    val client = PipeEnd(inbound = towardClient, outbound = towardServer)

    /** The transport handed to the build server session. */
    val server = PipeEnd(inbound = towardServer, outbound = towardClient)
}
