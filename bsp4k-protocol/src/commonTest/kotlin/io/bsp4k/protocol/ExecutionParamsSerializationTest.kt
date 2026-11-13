package io.bsp4k.protocol

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ExecutionParamsSerializationTest {
    private val target = BuildTargetIdentifier(uri = "file:///p/main")
    private val destination = DestinationIdentifier(uri = "file:///d/jvm")

    @Test
    fun `CompileParams omits optional members and encodes required targets`() {
        val params = CompileParams(targets = listOf(target))
        wireJson.encodeToString(CompileParams.serializer(), params) shouldBe """{"targets":[{"uri":"file:///p/main"}]}"""
    }

    @Test
    fun `CompileParams encodes destination`() {
        val params = CompileParams(targets = listOf(target), destination = destination, originId = "c1")
        val text =
            """{"targets":[{"uri":"file:///p/main"}],"destination":{"uri":"file:///d/jvm"},"originId":"c1"}"""
        wireJson.encodeToString(CompileParams.serializer(), params) shouldBe text
        wireJson.decodeFromString(CompileParams.serializer(), text) shouldBe params
    }

    @Test
    fun `TestParams encodes destination environment variables and working directory`() {
        val params =
            TestParams(
                targets = listOf(target),
                destination = destination,
                originId = "t1",
                arguments = listOf("--fast"),
                environmentVariables = mapOf("A" to "1"),
                workingDirectory = "file:///work",
            )
        val text =
            """{"targets":[{"uri":"file:///p/main"}],"destination":{"uri":"file:///d/jvm"},"originId":"t1",""" +
                """"arguments":["--fast"],"environmentVariables":{"A":"1"},"workingDirectory":"file:///work"}"""
        wireJson.encodeToString(TestParams.serializer(), params) shouldBe text
        wireJson.decodeFromString(TestParams.serializer(), text) shouldBe params
    }

    @Test
    fun `TestParams omits new optional members when absent`() {
        val params = TestParams(targets = listOf(target))
        wireJson.encodeToString(TestParams.serializer(), params) shouldBe """{"targets":[{"uri":"file:///p/main"}]}"""
    }

    @Test
    fun `RunParams encodes destination environment variables and working directory`() {
        val params =
            RunParams(
                target = target,
                destination = destination,
                originId = "r1",
                arguments = listOf("-v"),
                environmentVariables = mapOf("B" to "2"),
                workingDirectory = "file:///work",
            )
        val text =
            """{"target":{"uri":"file:///p/main"},"destination":{"uri":"file:///d/jvm"},"originId":"r1",""" +
                """"arguments":["-v"],"environmentVariables":{"B":"2"},"workingDirectory":"file:///work"}"""
        wireJson.encodeToString(RunParams.serializer(), params) shouldBe text
        wireJson.decodeFromString(RunParams.serializer(), text) shouldBe params
    }

    @Test
    fun `RunParams omits new optional members when absent`() {
        val params = RunParams(target = target)
        wireJson.encodeToString(RunParams.serializer(), params) shouldBe """{"target":{"uri":"file:///p/main"}}"""
    }

    @Test
    fun `DebugSessionParams encodes destination`() {
        val params = DebugSessionParams(targets = listOf(target), destination = destination)
        val text = """{"targets":[{"uri":"file:///p/main"}],"destination":{"uri":"file:///d/jvm"}}"""
        wireJson.encodeToString(DebugSessionParams.serializer(), params) shouldBe text
        wireJson.decodeFromString(DebugSessionParams.serializer(), text) shouldBe params
    }

    @Test
    fun `DebugSessionParams omits destination when absent`() {
        val params = DebugSessionParams(targets = listOf(target))
        wireJson.encodeToString(DebugSessionParams.serializer(), params) shouldBe
            """{"targets":[{"uri":"file:///p/main"}]}"""
    }

    @Test
    fun `DestinationsParams encodes required target`() {
        val params = DestinationsParams(target = target)
        wireJson.encodeToString(DestinationsParams.serializer(), params) shouldBe
            """{"target":{"uri":"file:///p/main"}}"""
    }

    @Test
    fun `DestinationsResult encodes required empty list and roundtrips`() {
        wireJson.encodeToString(DestinationsResult.serializer(), DestinationsResult(destinations = emptyList())) shouldBe
            """{"destinations":[]}"""
        val result = DestinationsResult(destinations = listOf(Destination(id = destination, displayName = "JVM")))
        val text = """{"destinations":[{"id":{"uri":"file:///d/jvm"},"displayName":"JVM"}]}"""
        wireJson.encodeToString(DestinationsResult.serializer(), result) shouldBe text
        wireJson.decodeFromString(DestinationsResult.serializer(), text) shouldBe result
    }

    @Test
    fun `destinations method constant matches spec`() {
        BspMethods.BUILD_TARGET_DESTINATIONS shouldBe "buildTarget/destinations"
    }
}
