package io.bsp4k.client

import io.jsonrpc4k.transport.StderrMode
import io.jsonrpc4k.transport.TransportException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BspLauncherTest {
    @Test
    fun `discovered server launches and completes the lifecycle`() =
        runBlocking {
            val directory = Files.createTempDirectory("bsp-launch-")
            val stderr = ByteArrayOutputStream()
            var launched: LaunchedBuildServer? = null
            try {
                val workspace = directory.resolve("workspace")
                val source = directory.resolve("BspFixturePeer.java")
                checkNotNull(javaClass.classLoader.getResourceAsStream("BspFixturePeer.java")).use { Files.copy(it, source) }
                val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
                val argv = listOf(java, "-Xmx128m", "-Dfile.encoding=UTF-8", "--source", "17", source.toString(), "normal")
                writeDetails(workspace.resolve(".bsp").resolve("fixture.json"), details("fixture", argv))

                val found = BspConnectionDiscovery(emptyList()).discover(workspace).connections.single()
                val options =
                    BspLaunchOptions(
                        stderr = StderrMode.Pipe { synchronized(stderr) { stderr.write(it) } },
                        initialize = true,
                    )
                val server = withTimeout(60_000) { buildClient { rootUri("file:///fixture") }.launch(found.details, workspace, options) }
                launched = server

                assertEquals("child-π", server.session.initializeResult?.displayName)
                assertTrue(withTimeout(10_000) { server.session.server.workspaceBuildTargets() }.targets.isEmpty())
                withTimeout(10_000) { server.session.shutdown() }
                assertNull(withTimeout(10_000) { server.session.awaitTermination() })
                assertEquals(0, withTimeout(10_000) { server.process.awaitExit() })
            } catch (failure: Throwable) {
                throw AssertionError("Launch failed; child stderr: ${synchronized(stderr) { stderr.toString(Charsets.UTF_8) }}", failure)
            } finally {
                launched?.close()
                directory.toFile().deleteRecursively()
            }
        }

    @Test
    fun `launch of a missing command fails with a transport error`() =
        runBlocking {
            val missing = details("missing", listOf("/nonexistent/bsp4k-no-such-server"))
            assertFailsWith<TransportException> {
                buildClient { rootUri("file:///fixture") }.launch(missing, Path.of(System.getProperty("java.io.tmpdir")))
            }
            Unit
        }
}
