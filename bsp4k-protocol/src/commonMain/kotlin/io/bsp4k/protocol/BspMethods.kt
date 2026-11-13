package io.bsp4k.protocol

/** BSP 2.1 method name constants. */
public object BspMethods {
    // Lifecycle
    public const val BUILD_INITIALIZE: String = "build/initialize"
    public const val BUILD_INITIALIZED: String = "build/initialized"
    public const val BUILD_SHUTDOWN: String = "build/shutdown"
    public const val BUILD_EXIT: String = "build/exit"

    // Workspace
    public const val WORKSPACE_BUILD_TARGETS: String = "workspace/buildTargets"
    public const val WORKSPACE_RELOAD: String = "workspace/reload"

    // Build Target
    public const val BUILD_TARGET_SOURCES: String = "buildTarget/sources"
    public const val BUILD_TARGET_INVERSE_SOURCES: String = "buildTarget/inverseSources"
    public const val BUILD_TARGET_DEPENDENCY_SOURCES: String = "buildTarget/dependencySources"
    public const val BUILD_TARGET_DEPENDENCY_MODULES: String = "buildTarget/dependencyModules"
    public const val BUILD_TARGET_RESOURCES: String = "buildTarget/resources"
    public const val BUILD_TARGET_OUTPUT_PATHS: String = "buildTarget/outputPaths"
    public const val BUILD_TARGET_COMPILE: String = "buildTarget/compile"
    public const val BUILD_TARGET_TEST: String = "buildTarget/test"
    public const val BUILD_TARGET_RUN: String = "buildTarget/run"
    public const val BUILD_TARGET_CLEAN_CACHE: String = "buildTarget/cleanCache"
    public const val DEBUG_SESSION_START: String = "debugSession/start"

    @Deprecated("The BSP method is debugSession/start", ReplaceWith("DEBUG_SESSION_START"))
    public const val BUILD_TARGET_DEBUG_SESSION: String = DEBUG_SESSION_START

    // Notifications (server -> client)
    public const val BUILD_LOG_MESSAGE: String = "build/logMessage"
    public const val BUILD_SHOW_MESSAGE: String = "build/showMessage"
    public const val BUILD_PUBLISH_DIAGNOSTICS: String = "build/publishDiagnostics"
    public const val BUILD_TARGET_DID_CHANGE: String = "buildTarget/didChange"
    public const val BUILD_TASK_START: String = "build/taskStart"
    public const val BUILD_TASK_PROGRESS: String = "build/taskProgress"
    public const val BUILD_TASK_FINISH: String = "build/taskFinish"

    // JVM Extensions
    public const val BUILD_TARGET_JVM_RUN_ENVIRONMENT: String = "buildTarget/jvmRunEnvironment"
    public const val BUILD_TARGET_JVM_TEST_ENVIRONMENT: String = "buildTarget/jvmTestEnvironment"
    public const val BUILD_TARGET_JVM_COMPILE_CLASSPATH: String = "buildTarget/jvmCompileClasspath"
}
