package io.bsp4k.protocol

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlin.test.Test

class LifecycleSerializationTest {
    private val json = wireJson
    private val jsonNoDefaults = Json

    @Test
    fun `InitializeBuildParams roundtrips`() {
        val original =
            InitializeBuildParams(
                displayName = "test-client",
                version = "1.0.0",
                bspVersion = "2.1.0",
                rootUri = "file:///workspace",
                capabilities = BuildClientCapabilities(languageIds = listOf("kotlin", "java")),
            )
        val encoded = json.encodeToString(InitializeBuildParams.serializer(), original)
        val decoded = json.decodeFromString(InitializeBuildParams.serializer(), encoded)
        decoded shouldBe original
    }

    @Test
    fun `InitializeBuildResult roundtrips with minimal capabilities`() {
        val original =
            InitializeBuildResult(
                displayName = "test-server",
                version = "1.0.0",
                bspVersion = "2.1.0",
                capabilities = BuildServerCapabilities(),
            )
        val encoded = jsonNoDefaults.encodeToString(InitializeBuildResult.serializer(), original)
        val decoded = jsonNoDefaults.decodeFromString(InitializeBuildResult.serializer(), encoded)
        decoded shouldBe original
    }

    @Test
    fun `InitializeBuildResult roundtrips with full capabilities`() {
        val original =
            InitializeBuildResult(
                displayName = "full-server",
                version = "2.0.0",
                bspVersion = "2.1.0",
                capabilities =
                    BuildServerCapabilities(
                        compileProvider = CompileProvider(languageIds = listOf("kotlin")),
                        testProvider = TestProvider(languageIds = listOf("kotlin")),
                        runProvider = RunProvider(languageIds = listOf("kotlin")),
                        debugProvider = DebugProvider(languageIds = listOf("kotlin")),
                        inverseSourcesProvider = true,
                        dependencySourcesProvider = true,
                        dependencyModulesProvider = true,
                        resourcesProvider = true,
                        outputPathsProvider = true,
                        buildTargetChangedProvider = true,
                        jvmRunEnvironmentProvider = true,
                        jvmTestEnvironmentProvider = true,
                        jvmCompileClasspathProvider = true,
                        canReload = true,
                    ),
            )
        val encoded = json.encodeToString(InitializeBuildResult.serializer(), original)
        val decoded = json.decodeFromString(InitializeBuildResult.serializer(), encoded)
        decoded shouldBe original
    }

    @Test
    fun `BuildClientCapabilities roundtrips`() {
        val original = BuildClientCapabilities(languageIds = listOf("kotlin", "java", "scala"))
        val encoded = json.encodeToString(BuildClientCapabilities.serializer(), original)
        val decoded = json.decodeFromString(BuildClientCapabilities.serializer(), encoded)
        decoded shouldBe original
    }

    @Test
    fun `BuildServerCapabilities minimal encodes without null fields`() {
        val caps = BuildServerCapabilities()
        val encoded = jsonNoDefaults.encodeToString(BuildServerCapabilities.serializer(), caps)
        encoded shouldBe "{}"
    }
}
