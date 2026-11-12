package io.bsp4k.server

import io.bsp4k.protocol.BspMethods
import io.bsp4k.protocol.CompileParams
import io.bsp4k.protocol.CompileResult
import io.bsp4k.protocol.StatusCode
import io.jsonrpc4k.core.NotificationHandler
import io.jsonrpc4k.core.RequestHandler
import io.jsonrpc4k.transport.Transport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HandlerRegistrationTest {
    @Test
    fun `server lifecycle and cancellation methods are reserved`() {
        listOf(BspMethods.BUILD_INITIALIZE, BspMethods.BUILD_SHUTDOWN).forEach { method ->
            assertFailsWith<IllegalArgumentException> { buildServer { onRequest(method) { null } } }
        }
        listOf(BspMethods.BUILD_INITIALIZED, BspMethods.BUILD_EXIT, "$/cancelRequest").forEach { method ->
            assertFailsWith<IllegalArgumentException> { buildServer { onNotification(method) {} } }
        }
    }

    @Test
    fun `duplicate registrations fail in either order`() {
        val compile: suspend (CompileParams) -> CompileResult = { CompileResult(statusCode = StatusCode.Ok) }
        assertFailsWith<IllegalArgumentException> {
            buildServer {
                buildTarget {
                    compile(compile)
                    compile(compile)
                }
            }
        }
        assertFailsWith<IllegalArgumentException> {
            buildServer {
                buildTarget { compile(compile) }
                onRequest(BspMethods.BUILD_TARGET_COMPILE) { null }
            }
        }
        assertFailsWith<IllegalArgumentException> {
            buildServer {
                onRequest(BspMethods.BUILD_TARGET_COMPILE) { null }
                buildTarget { compile(compile) }
            }
        }
        assertFailsWith<IllegalArgumentException> {
            buildServer {
                onNotification("custom") {}
                onNotification("custom") {}
            }
        }
        assertFailsWith<IllegalArgumentException> {
            buildServer {
                workspace {
                    reload {}
                    reload {}
                }
            }
        }
        assertFailsWith<IllegalArgumentException> {
            buildServer {
                onRequest(BspMethods.WORKSPACE_RELOAD) { null }
                workspace { reload {} }
            }
        }
        assertFailsWith<IllegalArgumentException> {
            buildServer {
                workspace { reload {} }
                onRequest(BspMethods.WORKSPACE_RELOAD) { null }
            }
        }
        val config =
            buildServer {
                onRequest("one") { null }
                onRequest("two") { null }
            }
        assertEquals(2, config.requestHandlers.size)
    }

    @Test
    fun `start rejects reserved methods from a copied config before using transport`() =
        runTest {
            val config = buildServer {}
            listOf(BspMethods.BUILD_INITIALIZE, BspMethods.BUILD_SHUTDOWN).forEach { method ->
                assertFailsWith<IllegalArgumentException> {
                    config.copy(requestHandlers = mapOf(method to RequestHandler { null })).start(UnusedTransport, backgroundScope)
                }
            }
            listOf(BspMethods.BUILD_INITIALIZED, BspMethods.BUILD_EXIT, "$/cancelRequest").forEach { method ->
                assertFailsWith<IllegalArgumentException> {
                    config.copy(notificationHandlers = mapOf(method to NotificationHandler {})).start(UnusedTransport, backgroundScope)
                }
            }
        }
}

private object UnusedTransport : Transport {
    override val incoming: Flow<ByteArray> get() = error("transport must not be used")
    override val isConnected: Boolean get() = error("transport must not be used")
    override suspend fun send(data: ByteArray): Unit = error("transport must not be used")
    override suspend fun close(): Unit = error("transport must not be used")
}
