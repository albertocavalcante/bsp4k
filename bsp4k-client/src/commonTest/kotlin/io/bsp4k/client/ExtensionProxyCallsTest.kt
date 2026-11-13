package io.bsp4k.client

import io.bsp4k.protocol.BspMethods
import io.bsp4k.protocol.BuildTargetIdentifier
import io.bsp4k.protocol.CppOptionsParams
import io.bsp4k.protocol.JavacOptionsParams
import io.bsp4k.protocol.JvmCompileClasspathParams
import io.bsp4k.protocol.JvmEnvironmentParams
import io.bsp4k.protocol.PythonOptionsParams
import io.bsp4k.protocol.RustWorkspaceParams
import io.bsp4k.protocol.ScalaMainClassesParams
import io.bsp4k.protocol.ScalaTestClassesParams
import io.bsp4k.protocol.ScalacOptionsParams
import io.bsp4k.protocol.SetCargoFeaturesParams
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ExtensionProxyCallsTest {
    private val targets = listOf(BuildTargetIdentifier("t"))
    private val sent = """{"targets":[$TARGET]}"""
    private val items = """{"items":[]}"""

    @Test
    fun `jvm runEnvironment`() =
        runTest {
            assertCall(BspMethods.BUILD_TARGET_JVM_RUN_ENVIRONMENT, sent, items) { jvm.runEnvironment(JvmEnvironmentParams(targets)) }
        }

    @Test
    fun `jvm testEnvironment`() =
        runTest {
            assertCall(BspMethods.BUILD_TARGET_JVM_TEST_ENVIRONMENT, sent, items) { jvm.testEnvironment(JvmEnvironmentParams(targets)) }
        }

    @Test
    fun `jvm compileClasspath`() =
        runTest {
            assertCall(BspMethods.BUILD_TARGET_JVM_COMPILE_CLASSPATH, sent, items) {
                jvm.compileClasspath(JvmCompileClasspathParams(targets))
            }
        }

    @Test
    fun `java javacOptions`() =
        runTest {
            assertCall(BspMethods.BUILD_TARGET_JAVAC_OPTIONS, sent, items) { java.javacOptions(JavacOptionsParams(targets)) }
        }

    @Test
    fun `scala scalacOptions`() =
        runTest {
            assertCall(BspMethods.BUILD_TARGET_SCALAC_OPTIONS, sent, items) { scala.scalacOptions(ScalacOptionsParams(targets)) }
        }

    @Test
    fun `scala scalaTestClasses`() =
        runTest {
            assertCall(BspMethods.BUILD_TARGET_SCALA_TEST_CLASSES, sent, items) {
                scala.scalaTestClasses(ScalaTestClassesParams(targets))
            }
        }

    @Test
    fun `scala scalaMainClasses`() =
        runTest {
            assertCall(BspMethods.BUILD_TARGET_SCALA_MAIN_CLASSES, sent, items) {
                scala.scalaMainClasses(ScalaMainClassesParams(targets))
            }
        }

    @Test
    fun `python pythonOptions`() =
        runTest {
            assertCall(BspMethods.BUILD_TARGET_PYTHON_OPTIONS, sent, items) { python.pythonOptions(PythonOptionsParams(targets)) }
        }

    @Test
    fun `cpp cppOptions`() =
        runTest {
            assertCall(BspMethods.BUILD_TARGET_CPP_OPTIONS, sent, items) { cpp.cppOptions(CppOptionsParams(targets)) }
        }

    @Test
    fun `rust rustWorkspace`() =
        runTest {
            val result = """{"packages":[],"rawDependencies":{},"dependencies":{},"resolvedTargets":[]}"""
            assertCall(BspMethods.BUILD_TARGET_RUST_WORKSPACE, sent, result) { rust.rustWorkspace(RustWorkspaceParams(targets)) }
        }

    @Test
    fun `cargo featuresState sends no params`() =
        runTest {
            assertCall(BspMethods.WORKSPACE_CARGO_FEATURES_STATE, null, """{"packagesFeatures":[]}""") { cargo.featuresState() }
        }

    @Test
    fun `cargo setFeatures`() =
        runTest {
            assertCall(BspMethods.WORKSPACE_SET_CARGO_FEATURES, """{"packageId":"p","features":["a"]}""", """{"statusCode":1}""") {
                cargo.setFeatures(SetCargoFeaturesParams(packageId = "p", features = setOf("a")))
            }
        }
}
