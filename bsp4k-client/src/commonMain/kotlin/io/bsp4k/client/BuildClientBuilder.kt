package io.bsp4k.client

import io.bsp4k.protocol.BSP_VERSION
import io.bsp4k.protocol.BspMethods
import io.bsp4k.protocol.BuildClientCapabilities
import io.bsp4k.protocol.DidChangeBuildTarget
import io.bsp4k.protocol.LogMessageParams
import io.bsp4k.protocol.PrintParams
import io.bsp4k.protocol.PublishDiagnosticsParams
import io.bsp4k.protocol.ShowMessageParams
import io.bsp4k.protocol.TaskFinishParams
import io.bsp4k.protocol.TaskProgressParams
import io.bsp4k.protocol.TaskStartParams
import io.jsonrpc4k.core.JsonRpcCodec
import io.jsonrpc4k.core.JsonRpcException
import io.jsonrpc4k.core.NotificationHandler
import kotlinx.serialization.serializer

/**
 * Builder for build client configuration and the server-to-client notification hooks.
 * Every BSP 2.2 server-to-client notification has one `on...` hook; a notification without a hook is ignored.
 */
@BspClientDsl
public class BuildClientBuilder {
    private var displayName: String = "bsp4k-client"
    private var version: String = "0.1.0"
    private var bspVersion: String = BSP_VERSION
    private var rootUri: String = ""
    private var capabilities = BuildClientCapabilities(languageIds = emptyList())
    private val notificationHandlers = mutableMapOf<String, NotificationHandler>()
    private val json = JsonRpcCodec.defaultJson

    private inline fun <reified P> handleNotification(
        method: String,
        crossinline handler: suspend (P) -> Unit,
    ) {
        notificationHandlers.putUniqueHandler(
            method,
            NotificationHandler { params ->
                val element = params ?: throw JsonRpcException.invalidParams("$method requires params")
                handler(json.decodeFromJsonElement(serializer<P>(), element))
            },
        )
    }

    /** Set client information. [bspVersion] defaults to the BSP version this library implements. */
    public fun clientInfo(
        name: String,
        version: String,
        bspVersion: String = BSP_VERSION,
    ) {
        displayName = name
        this.version = version
        this.bspVersion = bspVersion
    }

    /** Set the root URI for the workspace. */
    public fun rootUri(uri: String) {
        rootUri = uri
    }

    /** Configure client capabilities from language ids only. */
    public fun capabilities(languageIds: List<String>) {
        capabilities = BuildClientCapabilities(languageIds = languageIds)
    }

    /** Configure the full set of client capabilities. */
    public fun capabilities(value: BuildClientCapabilities) {
        capabilities = value
    }

    /** Handle `build/logMessage` notifications. */
    public fun onLogMessage(handler: suspend (LogMessageParams) -> Unit) {
        handleNotification(BspMethods.BUILD_LOG_MESSAGE, handler)
    }

    /** Handle `build/showMessage` notifications. */
    public fun onShowMessage(handler: suspend (ShowMessageParams) -> Unit) {
        handleNotification(BspMethods.BUILD_SHOW_MESSAGE, handler)
    }

    /** Handle `build/publishDiagnostics` notifications. */
    public fun onPublishDiagnostics(handler: suspend (PublishDiagnosticsParams) -> Unit) {
        handleNotification(BspMethods.BUILD_PUBLISH_DIAGNOSTICS, handler)
    }

    /** Handle `buildTarget/didChange` notifications. */
    public fun onBuildTargetDidChange(handler: suspend (DidChangeBuildTarget) -> Unit) {
        handleNotification(BspMethods.BUILD_TARGET_DID_CHANGE, handler)
    }

    /** Handle `build/taskStart` notifications. */
    public fun onTaskStart(handler: suspend (TaskStartParams) -> Unit) {
        handleNotification(BspMethods.BUILD_TASK_START, handler)
    }

    /** Handle `build/taskProgress` notifications. */
    public fun onTaskProgress(handler: suspend (TaskProgressParams) -> Unit) {
        handleNotification(BspMethods.BUILD_TASK_PROGRESS, handler)
    }

    /** Handle `build/taskFinish` notifications. */
    public fun onTaskFinish(handler: suspend (TaskFinishParams) -> Unit) {
        handleNotification(BspMethods.BUILD_TASK_FINISH, handler)
    }

    /** Handle `run/printStdout` notifications. */
    public fun onPrintStdout(handler: suspend (PrintParams) -> Unit) {
        handleNotification(BspMethods.RUN_PRINT_STDOUT, handler)
    }

    /** Handle `run/printStderr` notifications. */
    public fun onPrintStderr(handler: suspend (PrintParams) -> Unit) {
        handleNotification(BspMethods.RUN_PRINT_STDERR, handler)
    }

    /** Add a custom notification handler. */
    public fun onNotification(
        method: String,
        handler: NotificationHandler,
    ) {
        requireNotReserved(method)
        notificationHandlers.putUniqueHandler(method, handler)
    }

    internal fun build(): BuildClientConfig =
        BuildClientConfig(
            displayName = displayName,
            version = version,
            bspVersion = bspVersion,
            rootUri = rootUri,
            capabilities = capabilities,
            notificationHandlers = notificationHandlers.toMap(),
        )
}
