package io.bsp4k.protocol

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.SerializationException
import kotlin.test.Test

class BuildTargetSerializationTest {
    private val json = wireJson

    @Test
    fun `BuildTarget minimal roundtrips`() {
        val original =
            BuildTarget(
                id = BuildTargetIdentifier(uri = "file:///project/main"),
                tags = emptyList(),
                capabilities = BuildTargetCapabilities(),
                languageIds = emptyList(),
                dependencies = emptyList(),
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
                            tags = emptyList(),
                            capabilities = BuildTargetCapabilities(canCompile = true),
                            languageIds = listOf("kotlin"),
                            dependencies = emptyList(),
                        ),
                        BuildTarget(
                            id = BuildTargetIdentifier(uri = "file:///project/test"),
                            displayName = "test",
                            tags = emptyList(),
                            capabilities = BuildTargetCapabilities(canTest = true),
                            languageIds = listOf("kotlin"),
                            dependencies = emptyList(),
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

    @Test
    fun `BuildTarget minimal always encodes required members`() {
        val target =
            BuildTarget(
                id = BuildTargetIdentifier(uri = "file:///project/main"),
                tags = emptyList(),
                capabilities = BuildTargetCapabilities(),
                languageIds = emptyList(),
                dependencies = emptyList(),
            )
        val encoded = json.encodeToString(BuildTarget.serializer(), target)
        encoded shouldContain "\"tags\":[]"
        encoded shouldContain "\"languageIds\":[]"
        encoded shouldContain "\"dependencies\":[]"
    }

    @Test
    fun `SourceItem always encodes generated`() {
        val item = SourceItem(uri = "file:///src/Main.kt", kind = SourceItemKind.File, generated = false)
        json.encodeToString(SourceItem.serializer(), item) shouldContain "\"generated\":false"
    }

    @Test
    fun `BuildTarget decode fails when required members are missing`() {
        shouldThrow<SerializationException> {
            json.decodeFromString(BuildTarget.serializer(), """{"id":{"uri":"file:///p"},"capabilities":{}}""")
        }
    }
}
