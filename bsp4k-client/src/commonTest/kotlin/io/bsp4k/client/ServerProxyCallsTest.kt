package io.bsp4k.client

import io.bsp4k.protocol.BspMethods
import io.bsp4k.protocol.BuildTargetIdentifier
import io.bsp4k.protocol.CleanCacheParams
import io.bsp4k.protocol.CompileParams
import io.bsp4k.protocol.DebugSessionParams
import io.bsp4k.protocol.DependencyModulesParams
import io.bsp4k.protocol.DependencySourcesParams
import io.bsp4k.protocol.DestinationsParams
import io.bsp4k.protocol.InverseSourcesParams
import io.bsp4k.protocol.OutputPathsParams
import io.bsp4k.protocol.ReadParams
import io.bsp4k.protocol.ResourcesParams
import io.bsp4k.protocol.RunParams
import io.bsp4k.protocol.SourcesParams
import io.bsp4k.protocol.TestParams
import io.bsp4k.protocol.TextDocumentIdentifier
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerProxyCallsTest {
    private val target = BuildTargetIdentifier("t")
    private val targets = """{"targets":[$TARGET]}"""

    @Test
    fun `workspaceBuildTargets sends no params`() =
        runTest {
            assertCall(BspMethods.WORKSPACE_BUILD_TARGETS, null, """{"targets":[]}""") { workspaceBuildTargets() }
        }

    @Test
    fun `workspaceReload sends no params and accepts a null result`() =
        runTest {
            assertCall(BspMethods.WORKSPACE_RELOAD, null, "null") { workspaceReload() }
        }

    @Test
    fun `destinations`() =
        runTest {
            val sent = """{"target":$TARGET}"""
            assertCall(BspMethods.BUILD_TARGET_DESTINATIONS, sent, """{"destinations":[]}""") { destinations(DestinationsParams(target)) }
        }

    @Test
    fun `sources`() =
        runTest {
            assertCall(BspMethods.BUILD_TARGET_SOURCES, targets, """{"items":[]}""") { sources(SourcesParams(listOf(target))) }
        }

    @Test
    fun `inverseSources`() =
        runTest {
            assertCall(
                BspMethods.BUILD_TARGET_INVERSE_SOURCES,
                """{"textDocument":{"uri":"d"}}""",
                """{"targets":[]}""",
            ) { inverseSources(InverseSourcesParams(TextDocumentIdentifier("d"))) }
        }

    @Test
    fun `dependencySources`() =
        runTest {
            assertCall(BspMethods.BUILD_TARGET_DEPENDENCY_SOURCES, targets, """{"items":[]}""") {
                dependencySources(DependencySourcesParams(listOf(target)))
            }
        }

    @Test
    fun `dependencyModules`() =
        runTest {
            assertCall(BspMethods.BUILD_TARGET_DEPENDENCY_MODULES, targets, """{"items":[]}""") {
                dependencyModules(DependencyModulesParams(listOf(target)))
            }
        }

    @Test
    fun `resources`() =
        runTest {
            assertCall(BspMethods.BUILD_TARGET_RESOURCES, targets, """{"items":[]}""") { resources(ResourcesParams(listOf(target))) }
        }

    @Test
    fun `outputPaths`() =
        runTest {
            assertCall(BspMethods.BUILD_TARGET_OUTPUT_PATHS, targets, """{"items":[]}""") { outputPaths(OutputPathsParams(listOf(target))) }
        }

    @Test
    fun `compile`() =
        runTest {
            assertCall(BspMethods.BUILD_TARGET_COMPILE, targets, """{"statusCode":1}""") { compile(CompileParams(listOf(target))) }
        }

    @Test
    fun `test`() =
        runTest {
            assertCall(BspMethods.BUILD_TARGET_TEST, targets, """{"statusCode":1}""") { test(TestParams(listOf(target))) }
        }

    @Test
    fun `run`() =
        runTest {
            assertCall(BspMethods.BUILD_TARGET_RUN, """{"target":$TARGET}""", """{"statusCode":1}""") { run(RunParams(target)) }
        }

    @Test
    fun `cleanCache`() =
        runTest {
            assertCall(BspMethods.BUILD_TARGET_CLEAN_CACHE, targets, """{"cleaned":true}""") {
                cleanCache(CleanCacheParams(listOf(target)))
            }
        }

    @Test
    fun `debugSession uses the debugSession start wire method`() =
        runTest {
            assertEquals("debugSession/start", BspMethods.DEBUG_SESSION_START)
            assertCall(BspMethods.DEBUG_SESSION_START, targets, """{"uri":"tcp://x"}""") {
                debugSession(DebugSessionParams(listOf(target)))
            }
        }

    @Test
    fun `readStdin sends a run readStdin notification`() =
        runTest {
            assertNotify(BspMethods.RUN_READ_STDIN, """{"originId":"o","message":"in"}""") {
                readStdin(ReadParams(originId = "o", message = "in"))
            }
        }
}
