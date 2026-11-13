package io.bsp4k.protocol

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test

class JvmFamilyExtensionsTest {
    private val target = BuildTargetIdentifier("file:///ws/app")

    private inline fun <reified T> encode(value: T): JsonElement = wireJson.parseToJsonElement(wireJson.encodeToString(value))

    private fun parse(text: String): JsonElement = wireJson.parseToJsonElement(text)

    private val targetJson = """{"uri":"file:///ws/app"}"""

    // ==================== Method constants and data kinds ====================

    @Test
    fun `java and scala method constants match spec`() {
        BspMethods.BUILD_TARGET_JAVAC_OPTIONS shouldBe "buildTarget/javacOptions"
        BspMethods.BUILD_TARGET_SCALAC_OPTIONS shouldBe "buildTarget/scalacOptions"
        BspMethods.BUILD_TARGET_SCALA_TEST_CLASSES shouldBe "buildTarget/scalaTestClasses"
        BspMethods.BUILD_TARGET_SCALA_MAIN_CLASSES shouldBe "buildTarget/scalaMainClasses"
    }

    @Test
    fun `data kind constants match spec`() {
        JvmDataKinds.BUILD_TARGET shouldBe "jvm"
        JvmDataKinds.SOURCE_ITEM shouldBe "jvm"
        MavenDataKinds.DEPENDENCY_MODULE shouldBe "maven"
        SbtDataKinds.BUILD_TARGET shouldBe "sbt"
        ScalaDataKinds.BUILD_TARGET shouldBe "scala"
        ScalaDataKinds.DIAGNOSTIC shouldBe "scala"
        ScalaDataKinds.TEST_PARAMS shouldBe "scala-test"
        ScalaDataKinds.TEST_SUITES shouldBe "scala-test-suites"
        ScalaDataKinds.TEST_SUITES_SELECTION shouldBe "scala-test-suites-selection"
        ScalaDataKinds.MAIN_CLASS shouldBe "scala-main-class"
        ScalaDataKinds.ATTACH_REMOTE shouldBe "scala-attach-remote"
    }

    // ==================== jvm ====================

    @Test
    fun `JvmSourceItemData omits absent package name`() {
        encode(JvmSourceItemData()) shouldBe JsonObject(emptyMap())
        encode(JvmSourceItemData("my.example")) shouldBe parse("""{"packageName":"my.example"}""")
    }

    // ==================== java ====================

    @Test
    fun `JavacOptionsItem encodes all required members`() {
        val item = JavacOptionsItem(target, emptyList(), emptyList(), "file:///out")
        encode(item) shouldBe
            parse("""{"target":$targetJson,"options":[],"classpath":[],"classDirectory":"file:///out"}""")
    }

    @Test
    fun `JavacOptionsResult roundtrips`() {
        val result = JavacOptionsResult(listOf(JavacOptionsItem(target, listOf("-Xlint"), listOf("a.jar"), "out")))
        wireJson.decodeFromString<JavacOptionsResult>(wireJson.encodeToString(result)) shouldBe result
        encode(JavacOptionsParams(listOf(target))) shouldBe parse("""{"targets":[$targetJson]}""")
    }

    // ==================== maven ====================

    @Test
    fun `MavenDependencyModule omits optional scope and classifier`() {
        val module =
            MavenDependencyModule("org", "lib", "1.0", listOf(MavenDependencyModuleArtifact("file:///lib.jar")))
        encode(module) shouldBe
            parse("""{"organization":"org","name":"lib","version":"1.0","artifacts":[{"uri":"file:///lib.jar"}]}""")
    }

    @Test
    fun `MavenDependencyModule encodes classifier and scope`() {
        val module =
            MavenDependencyModule(
                "org",
                "lib",
                "1.0",
                listOf(MavenDependencyModuleArtifact("file:///lib-sources.jar", "sources")),
                "compile",
            )
        wireJson.decodeFromString<MavenDependencyModule>(wireJson.encodeToString(module)) shouldBe module
        (encode(module) as JsonObject)["scope"] shouldBe parse("\"compile\"")
    }

    // ==================== scala ====================

    private val scalaTarget =
        ScalaBuildTarget("org.scala-lang", "2.13.12", "2.13", ScalaPlatform.Jvm, listOf("file:///scala.jar"))

    private val scalaTargetJson =
        """{"scalaOrganization":"org.scala-lang","scalaVersion":"2.13.12","scalaBinaryVersion":"2.13",""" +
            """"platform":1,"jars":["file:///scala.jar"]}"""

    @Test
    fun `ScalaBuildTarget encodes platform as int and omits absent jvmBuildTarget`() {
        encode(scalaTarget) shouldBe parse(scalaTargetJson)
    }

    @Test
    fun `ScalaPlatform values roundtrip and unknown value is rejected`() {
        ScalaPlatform.entries.map { it.value } shouldBe listOf(1, 2, 3)
        wireJson.decodeFromString<ScalaPlatform>("3") shouldBe ScalaPlatform.Native
        shouldThrow<SerializationException> { wireJson.decodeFromString<ScalaPlatform>("9") }
    }

    @Test
    fun `ScalaBuildTarget decodes nested jvm build target`() {
        val text = scalaTargetJson.dropLast(1) + ""","jvmBuildTarget":{"javaVersion":"17"}}"""
        wireJson.decodeFromString<ScalaBuildTarget>(text) shouldBe
            scalaTarget.copy(jvmBuildTarget = JvmBuildTarget(javaVersion = "17"))
    }

    @Test
    fun `SbtBuildTarget encodes required members and omits parent`() {
        val sbt = SbtBuildTarget("1.9.7", listOf("a.b._"), scalaTarget, children = listOf(target))
        encode(sbt) shouldBe
            parse(
                """{"sbtVersion":"1.9.7","autoImports":["a.b._"],"scalaBuildTarget":$scalaTargetJson,""" +
                    """"children":[$targetJson]}""",
            )
    }

    @Test
    fun `ScalacOptionsItem encodes all required members`() {
        val item = ScalacOptionsItem(target, listOf("-deprecation"), emptyList(), "file:///out")
        encode(item) shouldBe
            parse(
                """{"target":$targetJson,"options":["-deprecation"],"classpath":[],"classDirectory":"file:///out"}""",
            )
        encode(ScalacOptionsParams(listOf(target))) shouldBe parse("""{"targets":[$targetJson]}""")
        encode(ScalacOptionsResult(emptyList())) shouldBe parse("""{"items":[]}""")
    }

    @Test
    fun `ScalaTestClasses shapes omit optional members`() {
        encode(ScalaTestClassesParams(listOf(target))) shouldBe parse("""{"targets":[$targetJson]}""")
        encode(ScalaTestClassesItem(target, classes = listOf("a.B"))) shouldBe
            parse("""{"target":$targetJson,"classes":["a.B"]}""")
        encode(ScalaTestClassesItem(target, "munit", listOf("a.B"))) shouldBe
            parse("""{"target":$targetJson,"framework":"munit","classes":["a.B"]}""")
    }

    @Test
    fun `ScalaMainClass uses class as JSON key and omits absent environment variables`() {
        val main = ScalaMainClass("a.Main", emptyList(), listOf("-Xmx1g"))
        encode(main) shouldBe parse("""{"class":"a.Main","arguments":[],"jvmOptions":["-Xmx1g"]}""")
        wireJson.decodeFromString<ScalaMainClass>("""{"class":"a.Main","arguments":["x"],"jvmOptions":[]}""") shouldBe
            ScalaMainClass("a.Main", listOf("x"), emptyList())
    }

    @Test
    fun `ScalaMainClasses shapes encode`() {
        encode(ScalaMainClassesParams(listOf(target), "o1")) shouldBe
            parse("""{"targets":[$targetJson],"originId":"o1"}""")
        val main = ScalaMainClass("a.Main", emptyList(), emptyList())
        val result = ScalaMainClassesResult(listOf(ScalaMainClassesItem(target, listOf(main))))
        encode(result) shouldBe
            parse(
                """{"items":[{"target":$targetJson,"classes":[{"class":"a.Main","arguments":[],"jvmOptions":[]}]}]}""",
            )
    }

    @Test
    fun `ScalaAttachRemote encodes as empty object and decodes`() {
        encode(ScalaAttachRemote) shouldBe JsonObject(emptyMap())
        wireJson.decodeFromString<ScalaAttachRemote>("{}") shouldBe ScalaAttachRemote
    }

    @Test
    fun `ScalaTestSuites and selection encode all required members`() {
        val suites = ScalaTestSuites(listOf(ScalaTestSuiteSelection("a.B", listOf("t1"))), emptyList(), emptyList())
        encode(suites) shouldBe
            parse("""{"suites":[{"className":"a.B","tests":["t1"]}],"jvmOptions":[],"environmentVariables":[]}""")
    }

    @Test
    fun `ScalaTestParams omits absent members`() {
        encode(ScalaTestParams()) shouldBe JsonObject(emptyMap())
        encode(ScalaTestParams(jvmOptions = listOf("-Xmx1g"))) shouldBe parse("""{"jvmOptions":["-Xmx1g"]}""")
    }

    @Test
    fun `ScalaDiagnostic nested actions roundtrip`() {
        val edit = ScalaTextEdit(Range(Position(1, 2), Position(1, 5)), "foo")
        val action = ScalaAction("Fix", edit = ScalaWorkspaceEdit(listOf(edit)))
        val diagnostic = ScalaDiagnostic(listOf(action))
        wireJson.decodeFromString<ScalaDiagnostic>(wireJson.encodeToString(diagnostic)) shouldBe diagnostic
        encode(ScalaDiagnostic()) shouldBe JsonObject(emptyMap())
        (encode(action) as JsonObject).keys shouldBe setOf("title", "edit")
    }
}
