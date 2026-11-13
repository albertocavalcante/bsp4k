package io.bsp4k.protocol

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class BspMethodsTest {
    // ==================== Lifecycle ====================

    @Test
    fun `BUILD_INITIALIZE matches spec`() {
        BspMethods.BUILD_INITIALIZE shouldBe "build/initialize"
    }

    @Test
    fun `BUILD_INITIALIZED matches spec`() {
        BspMethods.BUILD_INITIALIZED shouldBe "build/initialized"
    }

    @Test
    fun `BUILD_SHUTDOWN matches spec`() {
        BspMethods.BUILD_SHUTDOWN shouldBe "build/shutdown"
    }

    @Test
    fun `BUILD_EXIT matches spec`() {
        BspMethods.BUILD_EXIT shouldBe "build/exit"
    }

    // ==================== Workspace ====================

    @Test
    fun `WORKSPACE_BUILD_TARGETS matches spec`() {
        BspMethods.WORKSPACE_BUILD_TARGETS shouldBe "workspace/buildTargets"
    }

    @Test
    fun `WORKSPACE_RELOAD matches spec`() {
        BspMethods.WORKSPACE_RELOAD shouldBe "workspace/reload"
    }

    // ==================== Build Target ====================

    @Test
    fun `BUILD_TARGET_SOURCES matches spec`() {
        BspMethods.BUILD_TARGET_SOURCES shouldBe "buildTarget/sources"
    }

    @Test
    fun `BUILD_TARGET_INVERSE_SOURCES matches spec`() {
        BspMethods.BUILD_TARGET_INVERSE_SOURCES shouldBe "buildTarget/inverseSources"
    }

    @Test
    fun `BUILD_TARGET_DEPENDENCY_SOURCES matches spec`() {
        BspMethods.BUILD_TARGET_DEPENDENCY_SOURCES shouldBe "buildTarget/dependencySources"
    }

    @Test
    fun `BUILD_TARGET_DEPENDENCY_MODULES matches spec`() {
        BspMethods.BUILD_TARGET_DEPENDENCY_MODULES shouldBe "buildTarget/dependencyModules"
    }

    @Test
    fun `BUILD_TARGET_RESOURCES matches spec`() {
        BspMethods.BUILD_TARGET_RESOURCES shouldBe "buildTarget/resources"
    }

    @Test
    fun `BUILD_TARGET_OUTPUT_PATHS matches spec`() {
        BspMethods.BUILD_TARGET_OUTPUT_PATHS shouldBe "buildTarget/outputPaths"
    }

    @Test
    fun `BUILD_TARGET_COMPILE matches spec`() {
        BspMethods.BUILD_TARGET_COMPILE shouldBe "buildTarget/compile"
    }

    @Test
    fun `BUILD_TARGET_TEST matches spec`() {
        BspMethods.BUILD_TARGET_TEST shouldBe "buildTarget/test"
    }

    @Test
    fun `BUILD_TARGET_RUN matches spec`() {
        BspMethods.BUILD_TARGET_RUN shouldBe "buildTarget/run"
    }

    @Test
    fun `BUILD_TARGET_CLEAN_CACHE matches spec`() {
        BspMethods.BUILD_TARGET_CLEAN_CACHE shouldBe "buildTarget/cleanCache"
    }

    @Test
    fun `DEBUG_SESSION_START matches spec`() {
        BspMethods.DEBUG_SESSION_START shouldBe "debugSession/start"
    }

    // ==================== Notifications ====================

    @Test
    fun `BUILD_LOG_MESSAGE matches spec`() {
        BspMethods.BUILD_LOG_MESSAGE shouldBe "build/logMessage"
    }

    @Test
    fun `BUILD_SHOW_MESSAGE matches spec`() {
        BspMethods.BUILD_SHOW_MESSAGE shouldBe "build/showMessage"
    }

    @Test
    fun `BUILD_PUBLISH_DIAGNOSTICS matches spec`() {
        BspMethods.BUILD_PUBLISH_DIAGNOSTICS shouldBe "build/publishDiagnostics"
    }

    @Test
    fun `BUILD_TARGET_DID_CHANGE matches spec`() {
        BspMethods.BUILD_TARGET_DID_CHANGE shouldBe "buildTarget/didChange"
    }

    @Test
    fun `BUILD_TASK_START matches spec`() {
        BspMethods.BUILD_TASK_START shouldBe "build/taskStart"
    }

    @Test
    fun `BUILD_TASK_PROGRESS matches spec`() {
        BspMethods.BUILD_TASK_PROGRESS shouldBe "build/taskProgress"
    }

    @Test
    fun `BUILD_TASK_FINISH matches spec`() {
        BspMethods.BUILD_TASK_FINISH shouldBe "build/taskFinish"
    }

    // ==================== JVM Extensions ====================

    @Test
    fun `BUILD_TARGET_JVM_RUN_ENVIRONMENT matches spec`() {
        BspMethods.BUILD_TARGET_JVM_RUN_ENVIRONMENT shouldBe "buildTarget/jvmRunEnvironment"
    }

    @Test
    fun `BUILD_TARGET_JVM_TEST_ENVIRONMENT matches spec`() {
        BspMethods.BUILD_TARGET_JVM_TEST_ENVIRONMENT shouldBe "buildTarget/jvmTestEnvironment"
    }

    @Test
    fun `BUILD_TARGET_JVM_COMPILE_CLASSPATH matches spec`() {
        BspMethods.BUILD_TARGET_JVM_COMPILE_CLASSPATH shouldBe "buildTarget/jvmCompileClasspath"
    }
}
