package io.bsp4k.protocol

/** BSP 2.2 method name constants. */
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

    // Notifications (server -> client)
    public const val BUILD_LOG_MESSAGE: String = "build/logMessage"
    public const val BUILD_SHOW_MESSAGE: String = "build/showMessage"
    public const val BUILD_PUBLISH_DIAGNOSTICS: String = "build/publishDiagnostics"
    public const val BUILD_TARGET_DID_CHANGE: String = "buildTarget/didChange"
    public const val BUILD_TASK_START: String = "build/taskStart"
    public const val BUILD_TASK_PROGRESS: String = "build/taskProgress"
    public const val BUILD_TASK_FINISH: String = "build/taskFinish"

    // Destinations, run stdio and cancellation (BSP 2.2 core)
    public const val BUILD_TARGET_DESTINATIONS: String = "buildTarget/destinations"
    public const val RUN_PRINT_STDOUT: String = "run/printStdout"
    public const val RUN_PRINT_STDERR: String = "run/printStderr"
    public const val RUN_READ_STDIN: String = "run/readStdin"
    public const val CANCEL_REQUEST: String = "$/cancelRequest"

    // JVM Extensions
    public const val BUILD_TARGET_JVM_RUN_ENVIRONMENT: String = "buildTarget/jvmRunEnvironment"
    public const val BUILD_TARGET_JVM_TEST_ENVIRONMENT: String = "buildTarget/jvmTestEnvironment"
    public const val BUILD_TARGET_JVM_COMPILE_CLASSPATH: String = "buildTarget/jvmCompileClasspath"

    // Java and Scala Extensions
    public const val BUILD_TARGET_JAVAC_OPTIONS: String = "buildTarget/javacOptions"
    public const val BUILD_TARGET_SCALAC_OPTIONS: String = "buildTarget/scalacOptions"
    public const val BUILD_TARGET_SCALA_TEST_CLASSES: String = "buildTarget/scalaTestClasses"
    public const val BUILD_TARGET_SCALA_MAIN_CLASSES: String = "buildTarget/scalaMainClasses"

    // Python, C++, Rust and Cargo extensions
    public const val BUILD_TARGET_PYTHON_OPTIONS: String = "buildTarget/pythonOptions"
    public const val BUILD_TARGET_CPP_OPTIONS: String = "buildTarget/cppOptions"
    public const val BUILD_TARGET_RUST_WORKSPACE: String = "buildTarget/rustWorkspace"
    public const val WORKSPACE_CARGO_FEATURES_STATE: String = "workspace/cargoFeaturesState"
    public const val WORKSPACE_SET_CARGO_FEATURES: String = "workspace/setCargoFeatures"
}
