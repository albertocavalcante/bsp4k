package io.bsp4k.protocol

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlin.test.Test

class BuildTargetSerializationTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun `BuildTarget minimal roundtrips`() {
        val original =
            BuildTarget(
                id = BuildTargetIdentifier(uri = "file:///project/main"),
                capabilities = BuildTargetCapabilities(),
            )
        val encoded = json.encodeToString(BuildTarget.serializer(), original)
        val decoded = json.decodeFromString(BuildTarget.serializer(), encoded)
        decoded shouldBe original
    }

    @Test
    fun `BuildTarget full roundtrips`() {
        val original =
            BuildTarget(
                id = BuildTargetIdentifier(uri = "file:///project/main"),
                displayName = "main",
                baseDirectory = "file:///project",
                tags = listOf("library"),
                capabilities =
                    BuildTargetCapabilities(
                        canCompile = true,
                        canTest = true,
                        canRun = true,
                        canDebug = false,
                    ),
                languageIds = listOf("kotlin", "java"),
                dependencies =
                    listOf(
                        BuildTargetIdentifier(uri = "file:///project/lib"),
                    ),
            )
        val encoded = json.encodeToString(BuildTarget.serializer(), original)
        val decoded = json.decodeFromString(BuildTarget.serializer(), encoded)
        decoded shouldBe original
    }

    @Test
    fun `BuildTargetCapabilities defaults to all false`() {
        val caps = BuildTargetCapabilities()
        caps.canCompile shouldBe false
        caps.canTest shouldBe false
        caps.canRun shouldBe false
        caps.canDebug shouldBe false
    }

    @Test
    fun `BuildTargetCapabilities roundtrips`() {
        val original =
            BuildTargetCapabilities(
                canCompile = true,
                canTest = true,
                canRun = false,
                canDebug = true,
            )
        val encoded = json.encodeToString(BuildTargetCapabilities.serializer(), original)
        val decoded = json.decodeFromString(BuildTargetCapabilities.serializer(), encoded)
        decoded shouldBe original
    }

    @Test
    fun `WorkspaceBuildTargetsResult roundtrips`() {
        val original =
            WorkspaceBuildTargetsResult(
                targets =
                    listOf(
                        BuildTarget(
                            id = BuildTargetIdentifier(uri = "file:///project/main"),
                            displayName = "main",
                            capabilities = BuildTargetCapabilities(canCompile = true),
                            languageIds = listOf("kotlin"),
                        ),
                        BuildTarget(
                            id = BuildTargetIdentifier(uri = "file:///project/test"),
                            displayName = "test",
                            capabilities = BuildTargetCapabilities(canTest = true),
                            languageIds = listOf("kotlin"),
                        ),
                    ),
            )
        val encoded = json.encodeToString(WorkspaceBuildTargetsResult.serializer(), original)
        val decoded = json.decodeFromString(WorkspaceBuildTargetsResult.serializer(), encoded)
        decoded shouldBe original
    }

    @Test
    fun `WorkspaceBuildTargetsResult empty targets roundtrips`() {
        val original = WorkspaceBuildTargetsResult(targets = emptyList())
        val encoded = json.encodeToString(WorkspaceBuildTargetsResult.serializer(), original)
        val decoded = json.decodeFromString(WorkspaceBuildTargetsResult.serializer(), encoded)
        decoded shouldBe original
    }
}
