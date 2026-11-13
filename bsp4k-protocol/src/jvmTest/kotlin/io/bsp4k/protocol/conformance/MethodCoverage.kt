package io.bsp4k.protocol.conformance

/** Who sends a method. */
internal enum class Direction { ClientToServer, ServerToClient, Bidirectional }

/** A public function on a public class of bsp4k-server or bsp4k-client. */
internal data class Surface(
    val className: String,
    val function: String,
)

/**
 * How bsp4k exposes one method. [server] and [client] are null when jsonrpc4k handles the method below the
 * bsp4k API; [note] says why.
 */
internal data class Endpoint(
    val direction: Direction,
    val server: Surface?,
    val client: Surface?,
    val note: String = "",
)

private const val SERVER = "io.bsp4k.server"
private const val CLIENT = "io.bsp4k.client"

private fun request(
    serverClass: String,
    serverFunction: String,
    clientClass: String,
    clientFunction: String,
): Endpoint =
    Endpoint(
        Direction.ClientToServer,
        Surface("$SERVER.$serverClass", serverFunction),
        Surface("$CLIENT.$clientClass", clientFunction),
    )

private fun notification(
    proxyFunction: String,
    clientHook: String,
): Endpoint =
    Endpoint(
        Direction.ServerToClient,
        Surface("$SERVER.BuildClientProxy", proxyFunction),
        Surface("$CLIENT.BuildClientBuilder", clientHook),
    )

/** Method to its server hook and client call (client to server) or server API and client handler (reverse). */
internal val endpointTable: Map<String, Endpoint> =
    mapOf(
        "build/initialize" to
            request("LifecycleHooksBuilder", "onInitialize", "BuildClientSession", "initialize"),
        "build/initialized" to
            request("LifecycleHooksBuilder", "onInitialized", "BuildClientSession", "initialize")
                .copy(note = "The client session sends it as part of initialize"),
        "build/shutdown" to
            request("LifecycleHooksBuilder", "onShutdown", "BuildClientSession", "shutdown"),
        "build/exit" to
            request("LifecycleHooksBuilder", "onExit", "BuildClientSession", "shutdown")
                .copy(note = "The client session sends it as part of shutdown"),
        "workspace/buildTargets" to
            request("WorkspaceHandlersBuilder", "buildTargets", "BuildServerProxy", "workspaceBuildTargets"),
        "workspace/reload" to
            request("WorkspaceHandlersBuilder", "reload", "BuildServerProxy", "workspaceReload"),
        "buildTarget/destinations" to
            request("BuildTargetHandlersBuilder", "destinations", "BuildServerProxy", "destinations"),
        "buildTarget/sources" to
            request("BuildTargetHandlersBuilder", "sources", "BuildServerProxy", "sources"),
        "buildTarget/inverseSources" to
            request("BuildTargetHandlersBuilder", "inverseSources", "BuildServerProxy", "inverseSources"),
        "buildTarget/dependencySources" to
            request("BuildTargetHandlersBuilder", "dependencySources", "BuildServerProxy", "dependencySources"),
        "buildTarget/dependencyModules" to
            request("BuildTargetHandlersBuilder", "dependencyModules", "BuildServerProxy", "dependencyModules"),
        "buildTarget/resources" to
            request("BuildTargetHandlersBuilder", "resources", "BuildServerProxy", "resources"),
        "buildTarget/outputPaths" to
            request("BuildTargetHandlersBuilder", "outputPaths", "BuildServerProxy", "outputPaths"),
        "buildTarget/compile" to
            request("BuildTargetHandlersBuilder", "compile", "BuildServerProxy", "compile"),
        "buildTarget/test" to
            request("BuildTargetHandlersBuilder", "test", "BuildServerProxy", "test"),
        "buildTarget/run" to
            request("BuildTargetHandlersBuilder", "run", "BuildServerProxy", "run"),
        "buildTarget/cleanCache" to
            request("BuildTargetHandlersBuilder", "cleanCache", "BuildServerProxy", "cleanCache"),
        "debugSession/start" to
            request("DebugSessionHandlersBuilder", "start", "BuildServerProxy", "debugSession"),
        "run/readStdin" to
            request("RunHandlersBuilder", "readStdin", "BuildServerProxy", "readStdin"),
        "buildTarget/jvmRunEnvironment" to
            request("JvmHandlersBuilder", "runEnvironment", "JvmProxy", "runEnvironment"),
        "buildTarget/jvmTestEnvironment" to
            request("JvmHandlersBuilder", "testEnvironment", "JvmProxy", "testEnvironment"),
        "buildTarget/jvmCompileClasspath" to
            request("JvmHandlersBuilder", "compileClasspath", "JvmProxy", "compileClasspath"),
        "buildTarget/javacOptions" to
            request("JavaHandlersBuilder", "javacOptions", "JavaProxy", "javacOptions"),
        "buildTarget/scalacOptions" to
            request("ScalaHandlersBuilder", "scalacOptions", "ScalaProxy", "scalacOptions"),
        "buildTarget/scalaTestClasses" to
            request("ScalaHandlersBuilder", "scalaTestClasses", "ScalaProxy", "scalaTestClasses"),
        "buildTarget/scalaMainClasses" to
            request("ScalaHandlersBuilder", "scalaMainClasses", "ScalaProxy", "scalaMainClasses"),
        "buildTarget/pythonOptions" to
            request("PythonHandlersBuilder", "pythonOptions", "PythonProxy", "pythonOptions"),
        "buildTarget/cppOptions" to
            request("CppHandlersBuilder", "cppOptions", "CppProxy", "cppOptions"),
        "buildTarget/rustWorkspace" to
            request("RustHandlersBuilder", "rustWorkspace", "RustProxy", "rustWorkspace"),
        "workspace/cargoFeaturesState" to
            request("CargoHandlersBuilder", "featuresState", "CargoProxy", "featuresState"),
        "workspace/setCargoFeatures" to
            request("CargoHandlersBuilder", "setFeatures", "CargoProxy", "setFeatures"),
        "build/showMessage" to notification("showMessage", "onShowMessage"),
        "build/logMessage" to notification("logMessage", "onLogMessage"),
        "build/publishDiagnostics" to notification("publishDiagnostics", "onPublishDiagnostics"),
        "buildTarget/didChange" to notification("buildTargetDidChange", "onBuildTargetDidChange"),
        "build/taskStart" to notification("taskStart", "onTaskStart"),
        "build/taskProgress" to notification("taskProgress", "onTaskProgress"),
        "build/taskFinish" to notification("taskFinish", "onTaskFinish"),
        "run/printStdout" to notification("printStdout", "onPrintStdout"),
        "run/printStderr" to notification("printStderr", "onPrintStderr"),
        "\$/cancelRequest" to
            Endpoint(
                Direction.Bidirectional,
                server = null,
                client = null,
                note = "jsonrpc4k sends and consumes it; a session cancels the running handler",
            ),
    )
