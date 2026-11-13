package io.bsp4k.client

import io.bsp4k.protocol.BSP_VERSION
import io.bsp4k.protocol.BuildTargetIdentifier
import io.bsp4k.protocol.SourcesParams
import io.jsonrpc4k.core.JsonRpcException
import io.jsonrpc4k.core.ResponseError
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

class SessionLifecycleStateTest {
    private suspend fun TestScope.failingInit(initializeResult: String) {
        val peer = FakePeer(initializeResult = initializeResult)
        val session = buildClient {}.start(peer, backgroundScope)
        assertFailsWith<InitializeResultException> { session.initialize() }
        assertEquals(SessionState.FAILED, session.state)
        assertEquals(emptyList(), peer.notifications.map { it.method })
    }

    @Test
    fun `default bspVersion is the protocol constant and is sent on initialize`() =
        runTest {
            assertEquals(BSP_VERSION, buildClient {}.bspVersion)
            val peer = FakePeer()
            connect(peer)
            val params = assertNotNull(peer.requests.first().params).jsonObject
            assertEquals(BSP_VERSION, params["bspVersion"]?.jsonPrimitive?.content)
        }

    @Test
    fun `initialize exposes the server result then sends initialized`() =
        runTest {
            val result =
                """{"displayName":"srv","version":"1","bspVersion":"2.2.0",""" +
                    """"capabilities":{"compileProvider":{"languageIds":["kotlin"]}},"dataKind":"k","data":{"a":1}}"""
            val peer = FakePeer(initializeResult = result)
            val session = buildClient {}.start(peer, backgroundScope)
            assertNull(session.initializeResult)
            assertEquals(SessionState.NEW, session.state)
            val returned = session.initialize()
            assertEquals(returned, session.initializeResult)
            assertEquals(listOf("kotlin"), returned.capabilities.compileProvider?.languageIds)
            assertEquals("k", returned.dataKind)
            assertEquals(JsonObject(mapOf("a" to JsonPrimitive(1))), returned.data)
            assertEquals(SessionState.READY, session.state)
            // Notifications are written asynchronously; shutdown flushes them, so initialized must precede exit.
            session.shutdown()
            assertEquals(listOf("build/initialized", "build/exit"), peer.notifications.map { it.method })
        }

    @Test
    fun `blank displayName result is rejected before initialized`() =
        runTest { failingInit("""{"displayName":" ","version":"1","bspVersion":"2.2.0","capabilities":{}}""") }

    @Test
    fun `invalid bspVersion result is rejected before initialized`() =
        runTest { failingInit("""{"displayName":"s","version":"1","bspVersion":"two","capabilities":{}}""") }

    @Test
    fun `incompatible major bspVersion result is rejected before initialized`() =
        runTest { failingInit("""{"displayName":"s","version":"1","bspVersion":"3.0.0","capabilities":{}}""") }

    @Test
    fun `null initialize result is rejected before initialized`() = runTest { failingInit("null") }

    @Test
    fun `malformed initialize result is rejected before initialized`() = runTest { failingInit("""{"unexpected":true}""") }

    @Test
    fun `calls before initialize fail with a clear exception and send nothing`() =
        runTest {
            val peer = FakePeer()
            val session = buildClient {}.start(peer, backgroundScope)
            val failure = assertFailsWith<SessionStateException> { session.server.workspaceBuildTargets() }
            assertEquals(SessionState.NEW, failure.state)
            assertIs<IllegalStateException>(failure)
            assertFailsWith<SessionStateException> { session.server.sources(SourcesParams(listOf(BuildTargetIdentifier("t")))) }
            assertEquals(emptyList(), peer.requests)
        }

    @Test
    fun `calls after failed initialize are rejected`() =
        runTest {
            val peer = FakePeer(initializeResult = "null")
            val session = buildClient {}.start(peer, backgroundScope)
            assertFailsWith<InitializeResultException> { session.initialize() }
            assertFailsWith<SessionStateException> { session.server.workspaceReload() }
        }

    @Test
    fun `second initialize is rejected`() =
        runTest {
            val session = connect(FakePeer())
            assertFailsWith<SessionStateException> { session.initialize() }
        }

    @Test
    fun `calls after shutdown are rejected`() =
        runTest {
            val peer = FakePeer()
            val session = connect(peer)
            session.shutdown()
            assertEquals(SessionState.CLOSED, session.state)
            val failure = assertFailsWith<SessionStateException> { session.server.workspaceBuildTargets() }
            assertEquals(SessionState.CLOSED, failure.state)
            assertEquals(listOf("build/exit"), peer.notifications.map { it.method }.drop(1))
            assertFailsWith<SessionStateException> { session.shutdown() }
        }

    @Test
    fun `calls after close are rejected`() =
        runTest {
            val session = connect(FakePeer())
            session.close()
            assertFailsWith<SessionStateException> { session.server.workspaceReload() }
        }

    @Test
    fun `per call timeout replaces the default`() =
        runTest {
            val peer = FakePeer(silent = setOf("workspace/buildTargets"))
            val session = connect(peer)
            val failure = assertFailsWith<JsonRpcException> { session.server.workspaceBuildTargets(timeout = 50.milliseconds) }
            assertEquals(ResponseError.REQUEST_CANCELLED, failure.code)
        }

    @Test
    fun `initialize honors its timeout`() =
        runTest {
            val peer = FakePeer(silent = setOf("build/initialize"))
            val session = buildClient {}.start(peer, backgroundScope)
            val failure = assertFailsWith<JsonRpcException> { session.initialize(timeout = 50.milliseconds) }
            assertEquals(ResponseError.REQUEST_CANCELLED, failure.code)
            assertEquals(SessionState.FAILED, session.state)
        }

    @Test
    fun `server error responses surface as JsonRpcException`() =
        runTest {
            val peer = FakePeer(errors = mapOf("workspace/reload" to ResponseError(-32001, "nope")))
            val session = connect(peer)
            assertEquals(-32001, assertFailsWith<JsonRpcException> { session.server.workspaceReload() }.code)
        }

    @Test
    fun `null result for a typed request is an error`() =
        runTest {
            val session = connect(FakePeer(results = mapOf("workspace/buildTargets" to "null")))
            assertFailsWith<JsonRpcException> { session.server.workspaceBuildTargets() }
        }
}
