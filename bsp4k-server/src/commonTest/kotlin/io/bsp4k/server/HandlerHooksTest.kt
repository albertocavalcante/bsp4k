package io.bsp4k.server

import io.bsp4k.protocol.BspMethods
import io.bsp4k.protocol.BuildTargetIdentifier
import io.bsp4k.protocol.CargoFeaturesStateResult
import io.bsp4k.protocol.CleanCacheParams
import io.bsp4k.protocol.CleanCacheResult
import io.bsp4k.protocol.CompileParams
import io.bsp4k.protocol.CompileProvider
import io.bsp4k.protocol.CompileResult
import io.bsp4k.protocol.CppOptionsItem
import io.bsp4k.protocol.CppOptionsParams
import io.bsp4k.protocol.CppOptionsResult
import io.bsp4k.protocol.DebugProvider
import io.bsp4k.protocol.DebugSessionAddress
import io.bsp4k.protocol.DebugSessionParams
import io.bsp4k.protocol.DependencyModule
import io.bsp4k.protocol.DependencyModulesItem
import io.bsp4k.protocol.DependencyModulesParams
import io.bsp4k.protocol.DependencyModulesResult
import io.bsp4k.protocol.DependencySourcesItem
import io.bsp4k.protocol.DependencySourcesParams
import io.bsp4k.protocol.DependencySourcesResult
import io.bsp4k.protocol.Destination
import io.bsp4k.protocol.DestinationIdentifier
import io.bsp4k.protocol.DestinationsParams
import io.bsp4k.protocol.DestinationsResult
import io.bsp4k.protocol.InverseSourcesParams
import io.bsp4k.protocol.InverseSourcesResult
import io.bsp4k.protocol.JavacOptionsItem
import io.bsp4k.protocol.JavacOptionsParams
import io.bsp4k.protocol.JavacOptionsResult
import io.bsp4k.protocol.JvmCompileClasspathItem
import io.bsp4k.protocol.JvmCompileClasspathParams
import io.bsp4k.protocol.JvmCompileClasspathResult
import io.bsp4k.protocol.JvmEnvironmentItem
import io.bsp4k.protocol.JvmEnvironmentParams
import io.bsp4k.protocol.JvmEnvironmentResult
import io.bsp4k.protocol.OutputPathItem
import io.bsp4k.protocol.OutputPathItemKind
import io.bsp4k.protocol.OutputPathsItem
import io.bsp4k.protocol.OutputPathsParams
import io.bsp4k.protocol.OutputPathsResult
import io.bsp4k.protocol.PackageFeatures
import io.bsp4k.protocol.PythonOptionsItem
import io.bsp4k.protocol.PythonOptionsParams
import io.bsp4k.protocol.PythonOptionsResult
import io.bsp4k.protocol.ReadParams
import io.bsp4k.protocol.ResourcesItem
import io.bsp4k.protocol.ResourcesParams
import io.bsp4k.protocol.ResourcesResult
import io.bsp4k.protocol.RunParams
import io.bsp4k.protocol.RunProvider
import io.bsp4k.protocol.RunResult
import io.bsp4k.protocol.RustWorkspaceParams
import io.bsp4k.protocol.RustWorkspaceResult
import io.bsp4k.protocol.ScalaMainClass
import io.bsp4k.protocol.ScalaMainClassesItem
import io.bsp4k.protocol.ScalaMainClassesParams
import io.bsp4k.protocol.ScalaMainClassesResult
import io.bsp4k.protocol.ScalaTestClassesItem
import io.bsp4k.protocol.ScalaTestClassesParams
import io.bsp4k.protocol.ScalaTestClassesResult
import io.bsp4k.protocol.ScalacOptionsItem
import io.bsp4k.protocol.ScalacOptionsParams
import io.bsp4k.protocol.ScalacOptionsResult
import io.bsp4k.protocol.SetCargoFeaturesParams
import io.bsp4k.protocol.SetCargoFeaturesResult
import io.bsp4k.protocol.SourceItem
import io.bsp4k.protocol.SourceItemKind
import io.bsp4k.protocol.SourcesItem
import io.bsp4k.protocol.SourcesParams
import io.bsp4k.protocol.SourcesResult
import io.bsp4k.protocol.StatusCode
import io.bsp4k.protocol.TaskId
import io.bsp4k.protocol.TestParams
import io.bsp4k.protocol.TestProvider
import io.bsp4k.protocol.TestResult
import io.bsp4k.protocol.TextDocumentIdentifier
import io.bsp4k.protocol.WorkspaceBuildTargetsResult
import io.jsonrpc4k.core.ResponseError
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Every BSP 2.2 client-to-server method, core and extensions, reaches its typed hook over the wire. */
class HandlerHooksTest {
    private val target = BuildTargetIdentifier("file:///workspace/app")
    private val targets = listOf(target)
    private val received = mutableListOf<Any>()

    private fun <P : Any, R> answer(result: R): suspend BuildServerContext.(P) -> R =
        { params ->
            received += params
            result
        }

    private suspend inline fun <reified R> TestClient.expectResult(
        method: String,
        params: JsonElement?,
        result: R,
    ) {
        val response = request(method, params)
        assertNull(response.error, method)
        assertEquals(testJson.encodeToJsonElement(serializer<R>(), result), response.result, method)
    }

    private suspend inline fun <reified P, reified R> TestClient.expectRoundTrip(
        method: String,
        params: P,
        result: R,
    ) {
        expectResult(method, testJson.encodeToJsonElement(serializer<P>(), params), result)
    }

    private val sourcesResult =
        SourcesResult(listOf(SourcesItem(target, listOf(SourceItem("file:///workspace/app/A.kt", SourceItemKind.File, false)))))
    private val dependencySourcesResult = DependencySourcesResult(listOf(DependencySourcesItem(target, listOf("file:///a.jar"))))
    private val dependencyModulesResult =
        DependencyModulesResult(listOf(DependencyModulesItem(target, listOf(DependencyModule("a", "1")))))
    private val resourcesResult = ResourcesResult(listOf(ResourcesItem(target, listOf("file:///workspace/app/res"))))
    private val outputPathsResult =
        OutputPathsResult(listOf(OutputPathsItem(target, listOf(OutputPathItem("file:///out", OutputPathItemKind.Directory)))))
    private val destinationsResult =
        DestinationsResult(listOf(Destination(DestinationIdentifier("device://emulator"), "Emulator")))

    @Test
    fun `every core request reaches its hook`() =
        runTest {
            val (_, peer) =
                buildServer {
                    capabilities {
                        compileProvider = CompileProvider(listOf("kotlin"))
                        testProvider = TestProvider(listOf("kotlin"))
                        runProvider = RunProvider(listOf("kotlin"))
                        debugProvider = DebugProvider(listOf("kotlin"))
                        buildTargetDestinationsProvider = true
                        inverseSourcesProvider = true
                        dependencySourcesProvider = true
                        dependencyModulesProvider = true
                        resourcesProvider = true
                        outputPathsProvider = true
                        canReload = true
                    }
                    workspace {
                        buildTargets { WorkspaceBuildTargetsResult(emptyList()) }
                        reload { received += "reload" }
                    }
                    buildTarget {
                        sources(answer(sourcesResult))
                        inverseSources(answer(InverseSourcesResult(targets)))
                        dependencySources(answer(dependencySourcesResult))
                        dependencyModules(answer(dependencyModulesResult))
                        resources(answer(resourcesResult))
                        outputPaths(answer(outputPathsResult))
                        destinations(answer(destinationsResult))
                        compile(answer(CompileResult(originId = "c1", statusCode = StatusCode.Ok)))
                        test(answer(TestResult(originId = "t1", statusCode = StatusCode.Error)))
                        run(answer(RunResult(originId = "r1", statusCode = StatusCode.Cancelled)))
                        cleanCache(answer(CleanCacheResult(cleaned = true)))
                    }
                    debugSession { start(answer(DebugSessionAddress("tcp://localhost:5005"))) }
                    run { readStdin { params -> received += params } }
                }.connect(backgroundScope)
            peer.handshake()

            val sources = SourcesParams(targets)
            val inverseSources = InverseSourcesParams(TextDocumentIdentifier("file:///workspace/app/A.kt"))
            val dependencySources = DependencySourcesParams(targets)
            val dependencyModules = DependencyModulesParams(targets)
            val resources = ResourcesParams(targets)
            val outputPaths = OutputPathsParams(targets)
            val destinations = DestinationsParams(target)
            val compile = CompileParams(targets, originId = "c1")
            val test = TestParams(targets, originId = "t1")
            val run = RunParams(target, originId = "r1")
            val cleanCache = CleanCacheParams(targets)
            val debug = DebugSessionParams(targets)
            val stdin = ReadParams(originId = "r1", task = TaskId("run"), message = "line")

            peer.expectResult(BspMethods.WORKSPACE_BUILD_TARGETS, null, WorkspaceBuildTargetsResult(emptyList()))
            assertNullResult(peer.request(BspMethods.WORKSPACE_RELOAD))
            peer.expectRoundTrip(BspMethods.BUILD_TARGET_SOURCES, sources, sourcesResult)
            peer.expectRoundTrip(BspMethods.BUILD_TARGET_INVERSE_SOURCES, inverseSources, InverseSourcesResult(targets))
            peer.expectRoundTrip(BspMethods.BUILD_TARGET_DEPENDENCY_SOURCES, dependencySources, dependencySourcesResult)
            peer.expectRoundTrip(BspMethods.BUILD_TARGET_DEPENDENCY_MODULES, dependencyModules, dependencyModulesResult)
            peer.expectRoundTrip(BspMethods.BUILD_TARGET_RESOURCES, resources, resourcesResult)
            peer.expectRoundTrip(BspMethods.BUILD_TARGET_OUTPUT_PATHS, outputPaths, outputPathsResult)
            peer.expectRoundTrip(BspMethods.BUILD_TARGET_DESTINATIONS, destinations, destinationsResult)
            peer.expectRoundTrip(BspMethods.BUILD_TARGET_COMPILE, compile, CompileResult("c1", StatusCode.Ok))
            peer.expectRoundTrip(BspMethods.BUILD_TARGET_TEST, test, TestResult("t1", StatusCode.Error))
            peer.expectRoundTrip(BspMethods.BUILD_TARGET_RUN, run, RunResult("r1", StatusCode.Cancelled))
            peer.expectRoundTrip(BspMethods.BUILD_TARGET_CLEAN_CACHE, cleanCache, CleanCacheResult(cleaned = true))
            peer.expectRoundTrip(BspMethods.DEBUG_SESSION_START, debug, DebugSessionAddress("tcp://localhost:5005"))
            peer.notify(BspMethods.RUN_READ_STDIN, encode(ReadParams.serializer(), stdin))
            assertNull(peer.request(BspMethods.WORKSPACE_BUILD_TARGETS).error)

            val expected =
                listOf(
                    "reload",
                    sources,
                    inverseSources,
                    dependencySources,
                    dependencyModules,
                    resources,
                    outputPaths,
                    destinations,
                    compile,
                    test,
                    run,
                    cleanCache,
                    debug,
                    stdin,
                )
            assertEquals(expected, received)
        }

    @Test
    fun `every extension request reaches its hook`() =
        runTest {
            val jvmEnvironment =
                JvmEnvironmentResult(listOf(JvmEnvironmentItem(target, listOf("file:///a.jar"), emptyList(), "/w", emptyMap())))
            val classpath = JvmCompileClasspathResult(listOf(JvmCompileClasspathItem(target, listOf("file:///a.jar"))))
            val javac = JavacOptionsResult(listOf(JavacOptionsItem(target, listOf("-g"), emptyList(), "file:///classes")))
            val scalac = ScalacOptionsResult(listOf(ScalacOptionsItem(target, listOf("-Xlint"), emptyList(), "file:///classes")))
            val testClasses = ScalaTestClassesResult(listOf(ScalaTestClassesItem(target, classes = listOf("AppSpec"))))
            val mainClass = ScalaMainClass("Main", emptyList(), emptyList())
            val mainClasses = ScalaMainClassesResult(listOf(ScalaMainClassesItem(target, listOf(mainClass))))
            val python = PythonOptionsResult(listOf(PythonOptionsItem(target, listOf("-O"))))
            val rust = RustWorkspaceResult(emptyList(), emptyMap(), emptyMap(), targets)
            val packageFeatures = PackageFeatures("pkg", targets, mapOf("std" to emptySet<String>()), setOf("std"))
            val features = CargoFeaturesStateResult(listOf(packageFeatures))
            val cpp = CppOptionsResult(listOf(CppOptionsItem(target, listOf("-O2"), listOf("NDEBUG"), listOf("-lm"))))
            val (_, peer) =
                buildServer {
                    capabilities {
                        jvmRunEnvironmentProvider = true
                        jvmTestEnvironmentProvider = true
                        jvmCompileClasspathProvider = true
                        cargoFeaturesProvider = true
                    }
                    jvm {
                        runEnvironment(answer(jvmEnvironment))
                        testEnvironment(answer(jvmEnvironment))
                        compileClasspath(answer(classpath))
                    }
                    java { javacOptions(answer(javac)) }
                    scala {
                        scalacOptions(answer(scalac))
                        scalaTestClasses(answer(testClasses))
                        scalaMainClasses(answer(mainClasses))
                    }
                    python { pythonOptions(answer(python)) }
                    rust { rustWorkspace(answer(rust)) }
                    cargo {
                        featuresState { features }
                        setFeatures(answer(SetCargoFeaturesResult(StatusCode.Ok)))
                    }
                    cpp { cppOptions(answer(cpp)) }
                }.connect(backgroundScope)
            peer.handshake()

            peer.expectRoundTrip(BspMethods.BUILD_TARGET_JVM_RUN_ENVIRONMENT, JvmEnvironmentParams(targets, "o1"), jvmEnvironment)
            peer.expectRoundTrip(BspMethods.BUILD_TARGET_JVM_TEST_ENVIRONMENT, JvmEnvironmentParams(targets), jvmEnvironment)
            peer.expectRoundTrip(BspMethods.BUILD_TARGET_JVM_COMPILE_CLASSPATH, JvmCompileClasspathParams(targets), classpath)
            peer.expectRoundTrip(BspMethods.BUILD_TARGET_JAVAC_OPTIONS, JavacOptionsParams(targets), javac)
            peer.expectRoundTrip(BspMethods.BUILD_TARGET_SCALAC_OPTIONS, ScalacOptionsParams(targets), scalac)
            peer.expectRoundTrip(BspMethods.BUILD_TARGET_SCALA_TEST_CLASSES, ScalaTestClassesParams(targets), testClasses)
            peer.expectRoundTrip(BspMethods.BUILD_TARGET_SCALA_MAIN_CLASSES, ScalaMainClassesParams(targets), mainClasses)
            peer.expectRoundTrip(BspMethods.BUILD_TARGET_PYTHON_OPTIONS, PythonOptionsParams(targets), python)
            peer.expectRoundTrip(BspMethods.BUILD_TARGET_RUST_WORKSPACE, RustWorkspaceParams(targets), rust)
            peer.expectResult(BspMethods.WORKSPACE_CARGO_FEATURES_STATE, null, features)
            peer.expectRoundTrip(
                BspMethods.WORKSPACE_SET_CARGO_FEATURES,
                SetCargoFeaturesParams("pkg", setOf("std")),
                SetCargoFeaturesResult(StatusCode.Ok),
            )
            peer.expectRoundTrip(BspMethods.BUILD_TARGET_CPP_OPTIONS, CppOptionsParams(targets), cpp)

            assertEquals(
                listOf(
                    JvmEnvironmentParams(targets, "o1"),
                    JvmEnvironmentParams(targets),
                    JvmCompileClasspathParams(targets),
                    JavacOptionsParams(targets),
                    ScalacOptionsParams(targets),
                    ScalaTestClassesParams(targets),
                    ScalaMainClassesParams(targets),
                    PythonOptionsParams(targets),
                    RustWorkspaceParams(targets),
                    SetCargoFeaturesParams("pkg", setOf("std")),
                    CppOptionsParams(targets),
                ),
                received,
            )
        }

    @Test
    fun `missing or malformed params fail with InvalidParams`() =
        runTest {
            val (_, peer) = buildServer { buildTarget { sources(answer(sourcesResult)) } }.connect(backgroundScope)
            peer.handshake()

            assertError(ResponseError.INVALID_PARAMS, peer.request(BspMethods.BUILD_TARGET_SOURCES))
            val malformed = JsonObject(mapOf("targets" to JsonPrimitive("file:///workspace/app")))
            assertError(ResponseError.INVALID_PARAMS, peer.request(BspMethods.BUILD_TARGET_SOURCES, malformed))

            assertEquals(emptyList(), received)
        }
}
