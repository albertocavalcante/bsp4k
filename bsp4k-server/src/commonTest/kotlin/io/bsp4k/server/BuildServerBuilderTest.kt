package io.bsp4k.server

import io.bsp4k.protocol.BspMethods
import io.bsp4k.protocol.CompileProvider
import io.bsp4k.protocol.CompileResult
import io.bsp4k.protocol.DebugProvider
import io.bsp4k.protocol.RunProvider
import io.bsp4k.protocol.StatusCode
import io.bsp4k.protocol.TestProvider
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class BuildServerBuilderTest {
    // ==================== DSL Entry Point Tests ====================

    @Test
    fun `buildServer DSL builds config with defaults`() {
        val config = buildServer {}
        config.serverName shouldBe "bsp4k-server"
        config.serverVersion shouldBe "0.1.0"
        config.bspVersion shouldBe "2.1.0"
        config.requestHandlers shouldBe emptyMap()
        config.notificationHandlers shouldBe emptyMap()
    }

    @Test
    fun `buildServer DSL sets serverInfo`() {
        val config =
            buildServer {
                serverInfo("my-server", "2.0.0")
            }
        config.serverName shouldBe "my-server"
        config.serverVersion shouldBe "2.0.0"
        config.bspVersion shouldBe "2.1.0"
    }

    @Test
    fun `buildServer DSL sets serverInfo with custom bspVersion`() {
        val config =
            buildServer {
                serverInfo("my-server", "1.0.0", bspVersion = "2.2.0")
            }
        config.serverName shouldBe "my-server"
        config.serverVersion shouldBe "1.0.0"
        config.bspVersion shouldBe "2.2.0"
    }

    // ==================== Capabilities Tests ====================

    @Test
    fun `capabilities builder sets compileProvider`() {
        val config =
            buildServer {
                capabilities {
                    compileProvider = CompileProvider(languageIds = listOf("kotlin", "java"))
                }
            }
        config.capabilities.compileProvider.shouldNotBeNull()
        config.capabilities.compileProvider!!.languageIds shouldBe listOf("kotlin", "java")
    }

    @Test
    fun `capabilities builder sets testProvider`() {
        val config =
            buildServer {
                capabilities {
                    testProvider = TestProvider(languageIds = listOf("kotlin"))
                }
            }
        config.capabilities.testProvider.shouldNotBeNull()
    }

    @Test
    fun `capabilities builder sets runProvider`() {
        val config =
            buildServer {
                capabilities {
                    runProvider = RunProvider(languageIds = listOf("kotlin"))
                }
            }
        config.capabilities.runProvider.shouldNotBeNull()
    }

    @Test
    fun `capabilities builder sets debugProvider`() {
        val config =
            buildServer {
                capabilities {
                    debugProvider = DebugProvider(languageIds = listOf("kotlin"))
                }
            }
        config.capabilities.debugProvider.shouldNotBeNull()
    }

    @Test
    fun `capabilities builder sets boolean providers`() {
        val config =
            buildServer {
                capabilities {
                    inverseSourcesProvider = true
                    dependencySourcesProvider = true
                    dependencyModulesProvider = true
                    resourcesProvider = true
                    outputPathsProvider = true
                    buildTargetChangedProvider = true
                    canReload = true
                }
            }
        config.capabilities.inverseSourcesProvider shouldBe true
        config.capabilities.dependencySourcesProvider shouldBe true
        config.capabilities.dependencyModulesProvider shouldBe true
        config.capabilities.resourcesProvider shouldBe true
        config.capabilities.outputPathsProvider shouldBe true
        config.capabilities.buildTargetChangedProvider shouldBe true
        config.capabilities.canReload shouldBe true
    }

    @Test
    fun `capabilities builder sets JVM providers`() {
        val config =
            buildServer {
                capabilities {
                    jvmRunEnvironmentProvider = true
                    jvmTestEnvironmentProvider = true
                    jvmCompileClasspathProvider = true
                }
            }
        config.capabilities.jvmRunEnvironmentProvider shouldBe true
        config.capabilities.jvmTestEnvironmentProvider shouldBe true
        config.capabilities.jvmCompileClasspathProvider shouldBe true
    }

    @Test
    fun `capabilities builder leaves unset providers as null`() {
        val config =
            buildServer {
                capabilities {
                    compileProvider = CompileProvider(languageIds = listOf("kotlin"))
                }
            }
        config.capabilities.testProvider.shouldBeNull()
        config.capabilities.runProvider.shouldBeNull()
        config.capabilities.debugProvider.shouldBeNull()
        config.capabilities.inverseSourcesProvider shouldBe false
        config.capabilities.canReload shouldBe false
    }

    @Test
    fun `capabilities builder combines providers and booleans`() {
        val config =
            buildServer {
                capabilities {
                    compileProvider = CompileProvider(languageIds = listOf("kotlin"))
                    testProvider = TestProvider(languageIds = listOf("kotlin"))
                    inverseSourcesProvider = true
                    canReload = true
                }
            }
        config.capabilities.compileProvider.shouldNotBeNull()
        config.capabilities.testProvider.shouldNotBeNull()
        config.capabilities.inverseSourcesProvider shouldBe true
        config.capabilities.canReload shouldBe true
        config.capabilities.runProvider.shouldBeNull()
    }

    // ==================== BuildTarget Handler Registration Tests ====================

    @Test
    fun `buildTarget sources handler registration`() {
        val config =
            buildServer {
                buildTarget {
                    sources { _ -> error("stub") }
                }
            }
        config.requestHandlers shouldContainKey BspMethods.BUILD_TARGET_SOURCES
    }

    @Test
    fun `buildTarget inverseSources handler registration`() {
        val config =
            buildServer {
                buildTarget {
                    inverseSources { _ -> error("stub") }
                }
            }
        config.requestHandlers shouldContainKey BspMethods.BUILD_TARGET_INVERSE_SOURCES
    }

    @Test
    fun `buildTarget dependencySources handler registration`() {
        val config =
            buildServer {
                buildTarget {
                    dependencySources { _ -> error("stub") }
                }
            }
        config.requestHandlers shouldContainKey BspMethods.BUILD_TARGET_DEPENDENCY_SOURCES
    }

    @Test
    fun `buildTarget dependencyModules handler registration`() {
        val config =
            buildServer {
                buildTarget {
                    dependencyModules { _ -> error("stub") }
                }
            }
        config.requestHandlers shouldContainKey BspMethods.BUILD_TARGET_DEPENDENCY_MODULES
    }

    @Test
    fun `buildTarget resources handler registration`() {
        val config =
            buildServer {
                buildTarget {
                    resources { _ -> error("stub") }
                }
            }
        config.requestHandlers shouldContainKey BspMethods.BUILD_TARGET_RESOURCES
    }

    @Test
    fun `buildTarget outputPaths handler registration`() {
        val config =
            buildServer {
                buildTarget {
                    outputPaths { _ -> error("stub") }
                }
            }
        config.requestHandlers shouldContainKey BspMethods.BUILD_TARGET_OUTPUT_PATHS
    }

    @Test
    fun `buildTarget compile handler registration`() {
        val config =
            buildServer {
                buildTarget {
                    compile { _ -> CompileResult(statusCode = StatusCode.Ok) }
                }
            }
        config.requestHandlers shouldContainKey BspMethods.BUILD_TARGET_COMPILE
    }

    @Test
    fun `buildTarget test handler registration`() {
        val config =
            buildServer {
                buildTarget {
                    test { _ -> error("stub") }
                }
            }
        config.requestHandlers shouldContainKey BspMethods.BUILD_TARGET_TEST
    }

    @Test
    fun `buildTarget run handler registration`() {
        val config =
            buildServer {
                buildTarget {
                    run { _ -> error("stub") }
                }
            }
        config.requestHandlers shouldContainKey BspMethods.BUILD_TARGET_RUN
    }

    @Test
    fun `buildTarget cleanCache handler registration`() {
        val config =
            buildServer {
                buildTarget {
                    cleanCache { _ -> error("stub") }
                }
            }
        config.requestHandlers shouldContainKey BspMethods.BUILD_TARGET_CLEAN_CACHE
    }

    @Test
    fun `debugSession start handler registration`() {
        val config =
            buildServer {
                buildTarget {
                    debugSession { _ -> error("stub") }
                }
            }
        config.requestHandlers shouldContainKey BspMethods.DEBUG_SESSION_START
        config.requestHandlers shouldContainKey "debugSession/start"
    }

    // ==================== Workspace Handler Registration Tests ====================

    @Test
    fun `workspace buildTargets handler registration`() {
        val config =
            buildServer {
                workspace {
                    buildTargets { error("stub") }
                }
            }
        config.requestHandlers shouldContainKey BspMethods.WORKSPACE_BUILD_TARGETS
    }

    @Test
    fun `workspace reload handler registration`() {
        val config =
            buildServer {
                workspace {
                    reload { }
                }
            }
        config.requestHandlers shouldContainKey BspMethods.WORKSPACE_RELOAD
    }

    // ==================== JVM Handler Registration Tests ====================

    @Test
    fun `jvm runEnvironment handler registration`() {
        val config =
            buildServer {
                jvm {
                    runEnvironment { _ -> error("stub") }
                }
            }
        config.requestHandlers shouldContainKey BspMethods.BUILD_TARGET_JVM_RUN_ENVIRONMENT
    }

    @Test
    fun `jvm testEnvironment handler registration`() {
        val config =
            buildServer {
                jvm {
                    testEnvironment { _ -> error("stub") }
                }
            }
        config.requestHandlers shouldContainKey BspMethods.BUILD_TARGET_JVM_TEST_ENVIRONMENT
    }

    @Test
    fun `jvm compileClasspath handler registration`() {
        val config =
            buildServer {
                jvm {
                    compileClasspath { _ -> error("stub") }
                }
            }
        config.requestHandlers shouldContainKey BspMethods.BUILD_TARGET_JVM_COMPILE_CLASSPATH
    }

    // ==================== Custom Handler Tests ====================

    @Test
    fun `custom request handler registration`() {
        val config =
            buildServer {
                onRequest("custom/myMethod") { _ -> null }
            }
        config.requestHandlers shouldContainKey "custom/myMethod"
    }

    @Test
    fun `custom notification handler registration`() {
        val config =
            buildServer {
                onNotification("custom/myNotification") { _ -> }
            }
        config.notificationHandlers shouldContainKey "custom/myNotification"
    }

    @Test
    fun `custom handlers do not conflict with standard handlers`() {
        val config =
            buildServer {
                buildTarget {
                    compile { _ -> CompileResult(statusCode = StatusCode.Ok) }
                }
                onRequest("custom/myMethod") { _ -> null }
                onNotification("custom/myNotification") { _ -> }
            }
        config.requestHandlers shouldContainKey BspMethods.BUILD_TARGET_COMPILE
        config.requestHandlers shouldContainKey "custom/myMethod"
        config.notificationHandlers shouldContainKey "custom/myNotification"
    }

    // ==================== Handler Isolation Tests ====================

    @Test
    fun `handlers from different builder blocks are accumulated`() {
        val config =
            buildServer {
                buildTarget {
                    sources { _ -> error("stub") }
                    compile { _ -> CompileResult(statusCode = StatusCode.Ok) }
                }
                workspace {
                    buildTargets { error("stub") }
                }
                jvm {
                    runEnvironment { _ -> error("stub") }
                }
            }
        config.requestHandlers shouldContainKey BspMethods.BUILD_TARGET_SOURCES
        config.requestHandlers shouldContainKey BspMethods.BUILD_TARGET_COMPILE
        config.requestHandlers shouldContainKey BspMethods.WORKSPACE_BUILD_TARGETS
        config.requestHandlers shouldContainKey BspMethods.BUILD_TARGET_JVM_RUN_ENVIRONMENT
    }

    @Test
    fun `no handlers registered for unregistered methods`() {
        val config =
            buildServer {
                buildTarget {
                    compile { _ -> CompileResult(statusCode = StatusCode.Ok) }
                }
            }
        config.requestHandlers shouldContainKey BspMethods.BUILD_TARGET_COMPILE
        config.requestHandlers shouldNotContainKey BspMethods.BUILD_TARGET_SOURCES
        config.requestHandlers shouldNotContainKey BspMethods.BUILD_TARGET_TEST
        config.requestHandlers shouldNotContainKey BspMethods.WORKSPACE_BUILD_TARGETS
    }

    // ==================== Full Configuration Tests ====================

    @Test
    fun `full server configuration builds correctly`() {
        val config =
            buildServer {
                serverInfo("my-build-server", "1.0.0")
                capabilities {
                    compileProvider = CompileProvider(languageIds = listOf("kotlin", "java"))
                    testProvider = TestProvider(languageIds = listOf("kotlin", "java"))
                    runProvider = RunProvider(languageIds = listOf("kotlin"))
                    inverseSourcesProvider = true
                    dependencySourcesProvider = true
                    resourcesProvider = true
                    canReload = true
                }
                buildTarget {
                    sources { _ -> error("stub") }
                    inverseSources { _ -> error("stub") }
                    compile { _ -> CompileResult(statusCode = StatusCode.Ok) }
                    test { _ -> error("stub") }
                    run { _ -> error("stub") }
                    cleanCache { _ -> error("stub") }
                }
                workspace {
                    buildTargets { error("stub") }
                    reload { }
                }
                jvm {
                    runEnvironment { _ -> error("stub") }
                }
            }

        config.serverName shouldBe "my-build-server"
        config.serverVersion shouldBe "1.0.0"
        config.bspVersion shouldBe "2.1.0"

        config.capabilities.compileProvider.shouldNotBeNull()
        config.capabilities.testProvider.shouldNotBeNull()
        config.capabilities.runProvider.shouldNotBeNull()
        config.capabilities.inverseSourcesProvider shouldBe true
        config.capabilities.canReload shouldBe true

        config.requestHandlers.size shouldBe 9
        config.notificationHandlers shouldBe emptyMap()
    }
}
