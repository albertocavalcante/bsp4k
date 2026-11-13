package io.bsp4k.protocol

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlin.test.Test

class PythonCppExtensionSerializationTest {
    private val json = wireJson

    private fun tree(text: String) = Json.parseToJsonElement(text)

    @Test
    fun `PythonBuildTarget omits absent optionals`() {
        json.encodeToString(PythonBuildTarget.serializer(), PythonBuildTarget()) shouldBe "{}"
    }

    @Test
    fun `PythonBuildTarget golden`() {
        val encoded =
            json.encodeToString(
                PythonBuildTarget.serializer(),
                PythonBuildTarget(version = "3.12", interpreter = "file:///usr/bin/python3"),
            )
        tree(encoded) shouldBe tree("""{"version":"3.12","interpreter":"file:///usr/bin/python3"}""")
        PythonBuildTarget.KIND shouldBe "python"
    }

    @Test
    fun `PythonOptionsItem golden keeps required empty list`() {
        val encoded =
            json.encodeToString(
                PythonOptionsResult.serializer(),
                PythonOptionsResult(
                    items = listOf(PythonOptionsItem(BuildTargetIdentifier("t://a"), emptyList())),
                ),
            )
        tree(encoded) shouldBe tree("""{"items":[{"target":{"uri":"t://a"},"interpreterOptions":[]}]}""")
    }

    @Test
    fun `PythonOptionsParams decodes`() {
        val decoded =
            json.decodeFromString(PythonOptionsParams.serializer(), """{"targets":[{"uri":"t://a"}]}""")
        decoded shouldBe PythonOptionsParams(listOf(BuildTargetIdentifier("t://a")))
    }

    @Test
    fun `CppBuildTarget golden`() {
        val encoded =
            json.encodeToString(
                CppBuildTarget.serializer(),
                CppBuildTarget(version = "17", compiler = "clang", cCompiler = "file:///cc"),
            )
        tree(encoded) shouldBe tree("""{"version":"17","compiler":"clang","cCompiler":"file:///cc"}""")
        json.encodeToString(CppBuildTarget.serializer(), CppBuildTarget()) shouldBe "{}"
        CppBuildTarget.KIND shouldBe "cpp"
    }

    @Test
    fun `CppOptionsItem encodes required members and omits linkshared`() {
        val item =
            CppOptionsItem(
                target = BuildTargetIdentifier("t://a"),
                copts = emptyList(),
                defines = listOf("X=1"),
                linkopts = emptyList(),
            )
        tree(json.encodeToString(CppOptionsItem.serializer(), item)) shouldBe
            tree("""{"target":{"uri":"t://a"},"copts":[],"defines":["X=1"],"linkopts":[]}""")
        tree(json.encodeToString(CppOptionsItem.serializer(), item.copy(linkshared = false))) shouldBe
            tree("""{"target":{"uri":"t://a"},"copts":[],"defines":["X=1"],"linkopts":[],"linkshared":false}""")
    }

    @Test
    fun `CppOptionsResult roundtrips`() {
        val original =
            CppOptionsResult(
                listOf(CppOptionsItem(BuildTargetIdentifier("t://a"), listOf("-O2"), emptyList(), emptyList(), true)),
            )
        val encoded = json.encodeToString(CppOptionsResult.serializer(), original)
        json.decodeFromString(CppOptionsResult.serializer(), encoded) shouldBe original
    }

    @Test
    fun `method constants match spec`() {
        BspMethods.BUILD_TARGET_PYTHON_OPTIONS shouldBe "buildTarget/pythonOptions"
        BspMethods.BUILD_TARGET_CPP_OPTIONS shouldBe "buildTarget/cppOptions"
    }
}
