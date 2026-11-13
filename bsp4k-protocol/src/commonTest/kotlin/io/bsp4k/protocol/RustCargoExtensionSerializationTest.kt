package io.bsp4k.protocol

import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFailsWith

class RustCargoExtensionSerializationTest {
    private val json = wireJson

    private fun tree(text: String) = Json.parseToJsonElement(text)

    private fun target(kind: RustTargetKind = RustTargetKind.Lib) =
        RustTarget(
            name = "demo",
            crateRootUrl = "file:///src/lib.rs",
            kind = kind,
            edition = RustEdition.E2021,
            doctest = false,
        )

    @Test
    fun `RustTarget encodes required members including false doctest and omits optionals`() {
        tree(json.encodeToString(RustTarget.serializer(), target())) shouldBe
            tree(
                """{"name":"demo","crateRootUrl":"file:///src/lib.rs","kind":1,""" +
                    """"edition":"2021","doctest":false}""",
            )
    }

    @Test
    fun `RustTarget golden with crate types and features`() {
        val full =
            target(RustTargetKind.CustomBuild).copy(
                crateTypes = listOf(RustCrateType.ProcMacro, RustCrateType.Cdylib),
                requiredFeatures = setOf("a", "b"),
            )
        tree(json.encodeToString(RustTarget.serializer(), full)) shouldBe
            tree(
                """{"name":"demo","crateRootUrl":"file:///src/lib.rs","kind":6,"crateTypes":[7,5],""" +
                    """"edition":"2021","doctest":false,"requiredFeatures":["a","b"]}""",
            )
    }

    @Test
    fun `closed enums map every value`() {
        RustCrateType.entries.map { it.value } shouldBe (1..8).toList()
        RustTargetKind.entries.map { it.value } shouldBe (1..7).toList()
        json.decodeFromString(RustCrateType.serializer(), "8") shouldBe RustCrateType.Unknown
        json.decodeFromString(RustTargetKind.serializer(), "6") shouldBe RustTargetKind.CustomBuild
    }

    @Test
    fun `closed enums reject unknown values`() {
        assertFailsWith<SerializationException> { json.decodeFromString(RustCrateType.serializer(), "9") }
        assertFailsWith<SerializationException> { json.decodeFromString(RustTargetKind.serializer(), "0") }
    }

    @Test
    fun `open string enums preserve unknown values`() {
        val decoded =
            json.decodeFromString(
                RustDepKindInfo.serializer(),
                """{"kind":"future-kind","target":"cfg(unix)"}""",
            )
        decoded shouldBe RustDepKindInfo(kind = "future-kind", target = "cfg(unix)")
        tree(json.encodeToString(RustDepKindInfo.serializer(), decoded)) shouldBe
            tree("""{"kind":"future-kind","target":"cfg(unix)"}""")
        RustEdition.entries shouldBe listOf("2015", "2018", "2021")
        RustDepKind.entries shouldBe listOf("unclassified", "normal", "dev", "build")
        RustPackageOrigin.STDLIB_DEPENDENCY shouldBe "stdlib-dependency"
    }

    @Test
    fun `RustPackage decodes with unknown origin and set semantics`() {
        val text =
            """{"id":"p","rootUrl":"file:///p","name":"n","version":"1.0.0","origin":"vendored","edition":"2024",""" +
                """"resolvedTargets":[],"allTargets":[],"features":{"default":["a","a"]},""" +
                """"enabledFeatures":["a","a","b"]}"""
        val decoded = json.decodeFromString(RustPackage.serializer(), text)
        decoded.origin shouldBe "vendored"
        decoded.edition shouldBe "2024"
        decoded.enabledFeatures shouldBe setOf("a", "b")
        decoded.features shouldBe mapOf("default" to setOf("a"))
        decoded.source shouldBe null
    }

    @Test
    fun `RustPackage golden`() {
        val pkg =
            RustPackage(
                id = "p",
                rootUrl = "file:///p",
                name = "n",
                version = "1.0.0",
                origin = RustPackageOrigin.WORKSPACE,
                edition = RustEdition.E2018,
                resolvedTargets = listOf(target()),
                allTargets = emptyList(),
                features = mapOf("default" to emptySet()),
                enabledFeatures = emptySet(),
                env = mapOf("K" to "V"),
            )
        tree(json.encodeToString(RustPackage.serializer(), pkg)) shouldBe
            tree(
                """{"id":"p","rootUrl":"file:///p","name":"n","version":"1.0.0","origin":"workspace",""" +
                    """"edition":"2018","resolvedTargets":[{"name":"demo","crateRootUrl":"file:///src/lib.rs",""" +
                    """"kind":1,"edition":"2021","doctest":false}],"allTargets":[],"features":{"default":[]},""" +
                    """"enabledFeatures":[],"env":{"K":"V"}}""",
            )
    }

    @Test
    fun `RustRawDependency encodes required false booleans and empty features`() {
        val dep =
            RustRawDependency(
                name = "serde",
                optional = false,
                usesDefaultFeatures = false,
                features = emptySet(),
            )
        tree(json.encodeToString(RustRawDependency.serializer(), dep)) shouldBe
            tree("""{"name":"serde","optional":false,"usesDefaultFeatures":false,"features":[]}""")
    }

    @Test
    fun `RustWorkspaceResult golden`() {
        val rawDep =
            RustRawDependency(
                name = "a",
                kind = RustDepKind.DEV,
                optional = true,
                usesDefaultFeatures = true,
                features = setOf("f"),
            )
        val dep = RustDependency(pkg = "q", depKinds = listOf(RustDepKindInfo(RustDepKind.NORMAL)))
        val result =
            RustWorkspaceResult(
                packages = emptyList(),
                rawDependencies = mapOf("p" to listOf(rawDep)),
                dependencies = mapOf("p" to listOf(dep)),
                resolvedTargets = listOf(BuildTargetIdentifier("t://a")),
            )
        val encoded = json.encodeToString(RustWorkspaceResult.serializer(), result)
        tree(encoded) shouldBe
            tree(
                """{"packages":[],"rawDependencies":{"p":[{"name":"a","kind":"dev","optional":true,""" +
                    """"usesDefaultFeatures":true,"features":["f"]}]},"dependencies":{"p":[{"pkg":"q",""" +
                    """"depKinds":[{"kind":"normal"}]}]},"resolvedTargets":[{"uri":"t://a"}]}""",
            )
        json.decodeFromString(RustWorkspaceResult.serializer(), encoded) shouldBe result
    }

    @Test
    fun `CargoBuildTarget golden keeps empty required features`() {
        val encoded =
            json.encodeToString(CargoBuildTarget.serializer(), CargoBuildTarget(RustEdition.E2021, emptySet()))
        tree(encoded) shouldBe tree("""{"edition":"2021","requiredFeatures":[]}""")
        CargoBuildTarget.KIND shouldBe "cargo"
    }

    @Test
    fun `cargo features state roundtrips`() {
        val result =
            CargoFeaturesStateResult(
                listOf(
                    PackageFeatures(
                        packageId = "p",
                        targets = listOf(BuildTargetIdentifier("t://a")),
                        availableFeatures = mapOf("a" to setOf("b")),
                        enabledFeatures = setOf("a"),
                    ),
                ),
            )
        val encoded = json.encodeToString(CargoFeaturesStateResult.serializer(), result)
        tree(encoded) shouldBe
            tree(
                """{"packagesFeatures":[{"packageId":"p","targets":[{"uri":"t://a"}],""" +
                    """"availableFeatures":{"a":["b"]},"enabledFeatures":["a"]}]}""",
            )
        json.decodeFromString(CargoFeaturesStateResult.serializer(), encoded) shouldBe result
    }

    @Test
    fun `setCargoFeatures shapes golden`() {
        tree(json.encodeToString(SetCargoFeaturesParams.serializer(), SetCargoFeaturesParams("p", emptySet()))) shouldBe
            tree("""{"packageId":"p","features":[]}""")
        json.encodeToString(SetCargoFeaturesResult.serializer(), SetCargoFeaturesResult(StatusCode.Ok)) shouldBe
            """{"statusCode":1}"""
    }

    @Test
    fun `method constants match spec`() {
        BspMethods.BUILD_TARGET_RUST_WORKSPACE shouldBe "buildTarget/rustWorkspace"
        BspMethods.WORKSPACE_CARGO_FEATURES_STATE shouldBe "workspace/cargoFeaturesState"
        BspMethods.WORKSPACE_SET_CARGO_FEATURES shouldBe "workspace/setCargoFeatures"
    }
}
