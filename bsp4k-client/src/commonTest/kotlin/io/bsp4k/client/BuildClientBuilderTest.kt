package io.bsp4k.client

import io.bsp4k.protocol.BSP_VERSION
import io.bsp4k.protocol.BspMethods
import io.jsonrpc4k.core.NotificationHandler
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class BuildClientBuilderTest {
    // ==================== DSL Entry Point Tests ====================

    @Test
    fun `buildClient DSL builds config with defaults`() {
        val config = buildClient {}
        config.displayName shouldBe "bsp4k-client"
        config.version shouldBe "0.1.0"
        config.bspVersion shouldBe BSP_VERSION
        config.rootUri shouldBe ""
        config.notificationHandlers shouldBe emptyMap()
    }

    @Test
    fun `buildClient DSL sets clientInfo`() {
        val config =
            buildClient {
                clientInfo("test-client", "1.0.0")
            }
        config.displayName shouldBe "test-client"
        config.version shouldBe "1.0.0"
        config.bspVersion shouldBe BSP_VERSION
    }

    @Test
    fun `buildClient DSL sets clientInfo with custom bspVersion`() {
        val config =
            buildClient {
                clientInfo("my-client", "2.0.0", bspVersion = "2.2.0")
            }
        config.displayName shouldBe "my-client"
        config.version shouldBe "2.0.0"
        config.bspVersion shouldBe "2.2.0"
    }

    @Test
    fun `buildClient DSL sets rootUri`() {
        val config =
            buildClient {
                rootUri("file:///workspace/project")
            }
        config.rootUri shouldBe "file:///workspace/project"
    }

    @Test
    fun `buildClient DSL sets capabilities`() {
        val config =
            buildClient {
                capabilities(listOf("kotlin", "java"))
            }
        config.capabilities.languageIds shouldBe listOf("kotlin", "java")
    }

    // ==================== Notification Handler Registration Tests ====================

    @Test
    fun `onLogMessage handler registration`() {
        val config =
            buildClient {
                onLogMessage { _ -> }
            }
        config.notificationHandlers shouldContainKey BspMethods.BUILD_LOG_MESSAGE
    }

    @Test
    fun `onShowMessage handler registration`() {
        val config =
            buildClient {
                onShowMessage { _ -> }
            }
        config.notificationHandlers shouldContainKey BspMethods.BUILD_SHOW_MESSAGE
    }

    @Test
    fun `onPublishDiagnostics handler registration`() {
        val config =
            buildClient {
                onPublishDiagnostics { _ -> }
            }
        config.notificationHandlers shouldContainKey BspMethods.BUILD_PUBLISH_DIAGNOSTICS
    }

    @Test
    fun `onBuildTargetDidChange handler registration`() {
        val config =
            buildClient {
                onBuildTargetDidChange { _ -> }
            }
        config.notificationHandlers shouldContainKey BspMethods.BUILD_TARGET_DID_CHANGE
    }

    @Test
    fun `onTaskStart handler registration`() {
        val config =
            buildClient {
                onTaskStart { _ -> }
            }
        config.notificationHandlers shouldContainKey BspMethods.BUILD_TASK_START
    }

    @Test
    fun `onTaskProgress handler registration`() {
        val config =
            buildClient {
                onTaskProgress { _ -> }
            }
        config.notificationHandlers shouldContainKey BspMethods.BUILD_TASK_PROGRESS
    }

    @Test
    fun `onTaskFinish handler registration`() {
        val config =
            buildClient {
                onTaskFinish { _ -> }
            }
        config.notificationHandlers shouldContainKey BspMethods.BUILD_TASK_FINISH
    }

    @Test
    fun `multiple notification handlers can be registered`() {
        val config =
            buildClient {
                onLogMessage { _ -> }
                onShowMessage { _ -> }
                onPublishDiagnostics { _ -> }
                onBuildTargetDidChange { _ -> }
                onTaskStart { _ -> }
                onTaskProgress { _ -> }
                onTaskFinish { _ -> }
            }
        config.notificationHandlers.size shouldBe 7
    }

    // ==================== Custom Handler Registration Tests ====================

    @Test
    fun `custom notification handler registration`() {
        val config =
            buildClient {
                onNotification("custom/serverNotification", NotificationHandler { _ -> })
            }
        config.notificationHandlers shouldContainKey "custom/serverNotification"
    }

    @Test
    fun `custom handlers do not conflict with standard handlers`() {
        val config =
            buildClient {
                onLogMessage { _ -> }
                onNotification("custom/myNotification", NotificationHandler { _ -> })
            }
        config.notificationHandlers shouldContainKey BspMethods.BUILD_LOG_MESSAGE
        config.notificationHandlers shouldContainKey "custom/myNotification"
    }

    // ==================== Handler Isolation Tests ====================

    @Test
    fun `no handlers registered for unregistered methods`() {
        val config =
            buildClient {
                onLogMessage { _ -> }
            }
        config.notificationHandlers shouldContainKey BspMethods.BUILD_LOG_MESSAGE
        config.notificationHandlers shouldNotContainKey BspMethods.BUILD_SHOW_MESSAGE
        config.notificationHandlers shouldNotContainKey BspMethods.BUILD_PUBLISH_DIAGNOSTICS
        config.notificationHandlers shouldNotContainKey BspMethods.BUILD_TASK_START
    }

    // ==================== Full Configuration Tests ====================

    @Test
    fun `full client configuration builds correctly`() {
        val config =
            buildClient {
                clientInfo("my-editor", "3.0.0")
                rootUri("file:///home/user/project")
                capabilities(listOf("kotlin", "java"))

                onLogMessage { _ -> }
                onShowMessage { _ -> }
                onPublishDiagnostics { _ -> }
                onBuildTargetDidChange { _ -> }
                onTaskStart { _ -> }
                onTaskProgress { _ -> }
                onTaskFinish { _ -> }
            }

        config.displayName shouldBe "my-editor"
        config.version shouldBe "3.0.0"
        config.rootUri shouldBe "file:///home/user/project"
        config.capabilities.languageIds shouldBe listOf("kotlin", "java")
        config.notificationHandlers.size shouldBe 7
    }
}
