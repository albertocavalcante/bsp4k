package io.bsp4k.server

import io.bsp4k.protocol.BspMethods
import io.bsp4k.protocol.CompileParams
import io.bsp4k.protocol.CompileProvider
import io.bsp4k.protocol.CompileResult
import io.bsp4k.protocol.ReadParams
import io.bsp4k.protocol.StatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HandlerRegistrationTest {
    @Test
    fun `server lifecycle and cancellation methods are reserved`() {
        listOf(BspMethods.BUILD_INITIALIZE, BspMethods.BUILD_SHUTDOWN).forEach { method ->
            assertFailsWith<IllegalArgumentException> { buildServer { onRequest(method) { null } } }
        }
        listOf(BspMethods.BUILD_INITIALIZED, BspMethods.BUILD_EXIT, BspMethods.CANCEL_REQUEST).forEach { method ->
            assertFailsWith<IllegalArgumentException> { buildServer { onNotification(method) {} } }
        }
    }

    private fun assertDuplicate(block: BuildServerBuilder.() -> Unit) {
        val failure = assertFailsWith<IllegalArgumentException> { buildServer(block) }
        assertTrue(failure.message.orEmpty().startsWith("Handler already registered"), failure.message)
    }

    @Test
    fun `duplicate registrations fail in either order`() {
        val compileHandler: suspend BuildServerContext.(CompileParams) -> CompileResult =
            { CompileResult(statusCode = StatusCode.Ok) }
        val stdinHandler: suspend BuildServerContext.(ReadParams) -> Unit = {}
        assertDuplicate {
            buildTarget {
                compile(compileHandler)
                compile(compileHandler)
            }
        }
        assertDuplicate {
            buildTarget { compile(compileHandler) }
            onRequest(BspMethods.BUILD_TARGET_COMPILE) { null }
        }
        assertDuplicate {
            onRequest(BspMethods.BUILD_TARGET_COMPILE) { null }
            buildTarget { compile(compileHandler) }
        }
        assertDuplicate {
            onNotification("custom") {}
            onNotification("custom") {}
        }
        assertDuplicate {
            run { readStdin(stdinHandler) }
            onNotification(BspMethods.RUN_READ_STDIN) {}
        }
        assertDuplicate {
            workspace {
                reload {}
                reload {}
            }
        }
        assertDuplicate {
            onRequest(BspMethods.WORKSPACE_RELOAD) { null }
            workspace { reload {} }
        }
        val config =
            buildServer {
                capabilities { compileProvider = CompileProvider(listOf("kotlin")) }
                buildTarget { compile(compileHandler) }
                onRequest("one") { null }
                onRequest("two") { null }
            }
        assertEquals(3, config.requestHandlers.size)
    }

    @Test
    fun `a copied config rejects reserved methods`() {
        val config = buildServer {}
        val request: ServerRequestHandler = { null }
        val notification: ServerNotificationHandler = {}
        listOf(BspMethods.BUILD_INITIALIZE, BspMethods.BUILD_SHUTDOWN).forEach { method ->
            assertFailsWith<IllegalArgumentException> { config.copy(requestHandlers = mapOf(method to request)) }
        }
        listOf(BspMethods.BUILD_INITIALIZED, BspMethods.BUILD_EXIT, BspMethods.CANCEL_REQUEST).forEach { method ->
            assertFailsWith<IllegalArgumentException> { config.copy(notificationHandlers = mapOf(method to notification)) }
        }
    }
}
