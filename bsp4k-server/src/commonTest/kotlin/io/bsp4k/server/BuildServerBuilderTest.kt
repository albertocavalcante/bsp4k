package io.bsp4k.server

import io.bsp4k.protocol.BSP_VERSION
import io.bsp4k.protocol.BspMethods
import io.bsp4k.protocol.BuildServerCapabilities
import io.bsp4k.protocol.CompileProvider
import io.bsp4k.protocol.CompileResult
import io.bsp4k.protocol.DebugProvider
import io.bsp4k.protocol.RunProvider
import io.bsp4k.protocol.StatusCode
import io.bsp4k.protocol.TestProvider
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test
import kotlin.test.assertFailsWith

class BuildServerBuilderTest {
    /** A config with every capability-gated handler and its provider, plus every ungated handler. */
    private fun everyHandler(): BuildServerConfig =
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
                buildTargetChangedProvider = true
                jvmRunEnvironmentProvider = true
                jvmTestEnvironmentProvider = true
                jvmCompileClasspathProvider = true
                cargoFeaturesProvider = true
                canReload = true
            }
            workspace {
                buildTargets { error("stub") }
                reload {}
            }
            buildTarget {
                sources { error("stub") }
                inverseSources { error("stub") }
                dependencySources { error("stub") }
                dependencyModules { error("stub") }
                resources { error("stub") }
                outputPaths { error("stub") }
                destinations { error("stub") }
                compile { error("stub") }
                test { error("stub") }
                run { error("stub") }
                cleanCache { error("stub") }
            }
            debugSession { start { error("stub") } }
            run { readStdin {} }
            jvm {
                runEnvironment { error("stub") }
                testEnvironment { error("stub") }
                compileClasspath { error("stub") }
            }
            java { javacOptions { error("stub") } }
            scala {
                scalacOptions { error("stub") }
                scalaTestClasses { error("stub") }
                scalaMainClasses { error("stub") }
            }
            python { pythonOptions { error("stub") } }
            rust { rustWorkspace { error("stub") } }
            cargo {
                featuresState { error("stub") }
                setFeatures { error("stub") }
            }
            cpp { cppOptions { error("stub") } }
        }

    @Test
    fun `defaults use the protocol BSP version`() {
        val config = buildServer {}
        config.serverName shouldBe "bsp4k-server"
        config.serverVersion shouldBe "0.1.0"
        config.bspVersion shouldBe BSP_VERSION
        config.bspVersion shouldBe "2.2.0"
        config.capabilities shouldBe BuildServerCapabilities()
        config.requestHandlers shouldBe emptyMap()
        config.notificationHandlers shouldBe emptyMap()
        config.lifecycleHooks shouldBe LifecycleHooks()
    }

    @Test
    fun `serverInfo sets name version and optional bspVersion`() {
        buildServer { serverInfo("my-server", "2.0.0") }.bspVersion shouldBe BSP_VERSION
        val config = buildServer { serverInfo("my-server", "1.0.0", bspVersion = "2.1.0") }
        config.serverName shouldBe "my-server"
        config.serverVersion shouldBe "1.0.0"
        config.bspVersion shouldBe "2.1.0"
    }

    @Test
    fun `every builder registers its BSP 2 2 wire method`() {
        val config = everyHandler()
        config.requestHandlers.keys shouldBe
            setOf(
                BspMethods.WORKSPACE_BUILD_TARGETS,
                BspMethods.WORKSPACE_RELOAD,
                BspMethods.BUILD_TARGET_SOURCES,
                BspMethods.BUILD_TARGET_INVERSE_SOURCES,
                BspMethods.BUILD_TARGET_DEPENDENCY_SOURCES,
                BspMethods.BUILD_TARGET_DEPENDENCY_MODULES,
                BspMethods.BUILD_TARGET_RESOURCES,
                BspMethods.BUILD_TARGET_OUTPUT_PATHS,
                BspMethods.BUILD_TARGET_DESTINATIONS,
                BspMethods.BUILD_TARGET_COMPILE,
                BspMethods.BUILD_TARGET_TEST,
                BspMethods.BUILD_TARGET_RUN,
                BspMethods.BUILD_TARGET_CLEAN_CACHE,
                BspMethods.DEBUG_SESSION_START,
                BspMethods.BUILD_TARGET_JVM_RUN_ENVIRONMENT,
                BspMethods.BUILD_TARGET_JVM_TEST_ENVIRONMENT,
                BspMethods.BUILD_TARGET_JVM_COMPILE_CLASSPATH,
                BspMethods.BUILD_TARGET_JAVAC_OPTIONS,
                BspMethods.BUILD_TARGET_SCALAC_OPTIONS,
                BspMethods.BUILD_TARGET_SCALA_TEST_CLASSES,
                BspMethods.BUILD_TARGET_SCALA_MAIN_CLASSES,
                BspMethods.BUILD_TARGET_PYTHON_OPTIONS,
                BspMethods.BUILD_TARGET_RUST_WORKSPACE,
                BspMethods.WORKSPACE_CARGO_FEATURES_STATE,
                BspMethods.WORKSPACE_SET_CARGO_FEATURES,
                BspMethods.BUILD_TARGET_CPP_OPTIONS,
            )
        config.notificationHandlers.keys shouldBe setOf(BspMethods.RUN_READ_STDIN)
    }

    @Test
    fun `capabilities builder maps every flag`() {
        val capabilities = everyHandler().capabilities
        capabilities shouldBe
            BuildServerCapabilities(
                compileProvider = CompileProvider(listOf("kotlin")),
                testProvider = TestProvider(listOf("kotlin")),
                runProvider = RunProvider(listOf("kotlin")),
                debugProvider = DebugProvider(listOf("kotlin")),
                buildTargetDestinationsProvider = true,
                inverseSourcesProvider = true,
                dependencySourcesProvider = true,
                dependencyModulesProvider = true,
                resourcesProvider = true,
                outputPathsProvider = true,
                buildTargetChangedProvider = true,
                jvmRunEnvironmentProvider = true,
                jvmTestEnvironmentProvider = true,
                cargoFeaturesProvider = true,
                canReload = true,
                jvmCompileClasspathProvider = true,
            )
    }

    @Test
    fun `a declared provider without its handler fails fast`() {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                buildServer {
                    capabilities {
                        compileProvider = CompileProvider(listOf("kotlin"))
                        canReload = true
                    }
                }
            }
        val message = failure.message.orEmpty()
        message shouldContain "compileProvider is declared but buildTarget/compile has no handler"
        message shouldContain "canReload is declared but workspace/reload has no handler"
    }

    @Test
    fun `a gated handler without its provider fails fast`() {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                buildServer {
                    debugSession { start { error("stub") } }
                    cargo { featuresState { error("stub") } }
                }
            }
        val message = failure.message.orEmpty()
        message shouldContain "debugSession/start has a handler but debugProvider is not declared"
        message shouldContain "workspace/cargoFeaturesState has a handler but cargoFeaturesProvider is not declared"
    }

    @Test
    fun `a copied config is validated too`() {
        val config = buildServer { buildTarget { sources { error("stub") } } }
        val inconsistent = BuildServerCapabilities(resourcesProvider = true)
        assertFailsWith<IllegalArgumentException> { config.copy(capabilities = inconsistent) }
        config.copy(serverName = "renamed").serverName shouldBe "renamed"
    }

    @Test
    fun `ungated handlers need no capability`() {
        val config =
            buildServer {
                buildTarget {
                    sources { error("stub") }
                    cleanCache { error("stub") }
                }
                java { javacOptions { error("stub") } }
                run { readStdin {} }
            }
        config.requestHandlers.size shouldBe 3
        config.notificationHandlers.size shouldBe 1
    }

    @Test
    fun `handlers from repeated blocks accumulate`() {
        val config =
            buildServer {
                capabilities { compileProvider = CompileProvider(listOf("kotlin")) }
                buildTarget { sources { error("stub") } }
                buildTarget { compile { CompileResult(statusCode = StatusCode.Ok) } }
                lifecycle { onInitialized {} }
                lifecycle { onExit {} }
                onRequest("custom/method") { null }
                onNotification("custom/notification") {}
            }
        config.requestHandlers.keys shouldBe
            setOf(BspMethods.BUILD_TARGET_SOURCES, BspMethods.BUILD_TARGET_COMPILE, "custom/method")
        config.notificationHandlers.keys shouldBe setOf("custom/notification")
        (config.lifecycleHooks.onInitialized != null) shouldBe true
        (config.lifecycleHooks.onExit != null) shouldBe true
        config.lifecycleHooks.onInitialize shouldBe null
    }
}
