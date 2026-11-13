package io.bsp4k.client

import io.bsp4k.protocol.BspMethods
import io.bsp4k.protocol.CargoFeaturesStateResult
import io.bsp4k.protocol.CppOptionsParams
import io.bsp4k.protocol.CppOptionsResult
import io.bsp4k.protocol.JavacOptionsParams
import io.bsp4k.protocol.JavacOptionsResult
import io.bsp4k.protocol.JvmCompileClasspathParams
import io.bsp4k.protocol.JvmCompileClasspathResult
import io.bsp4k.protocol.JvmEnvironmentParams
import io.bsp4k.protocol.JvmEnvironmentResult
import io.bsp4k.protocol.PythonOptionsParams
import io.bsp4k.protocol.PythonOptionsResult
import io.bsp4k.protocol.RustWorkspaceParams
import io.bsp4k.protocol.RustWorkspaceResult
import io.bsp4k.protocol.ScalaMainClassesParams
import io.bsp4k.protocol.ScalaMainClassesResult
import io.bsp4k.protocol.ScalaTestClassesParams
import io.bsp4k.protocol.ScalaTestClassesResult
import io.bsp4k.protocol.ScalacOptionsParams
import io.bsp4k.protocol.ScalacOptionsResult
import io.bsp4k.protocol.SetCargoFeaturesParams
import io.bsp4k.protocol.SetCargoFeaturesResult
import kotlin.time.Duration

/** JVM extension requests. */
public class JvmProxy internal constructor(
    private val caller: RpcCaller,
) {
    /** `buildTarget/jvmRunEnvironment`. */
    public suspend fun runEnvironment(
        params: JvmEnvironmentParams,
        timeout: Duration = defaultRequestTimeout,
    ): JvmEnvironmentResult = caller.request(BspMethods.BUILD_TARGET_JVM_RUN_ENVIRONMENT, params, timeout)

    /** `buildTarget/jvmTestEnvironment`. */
    public suspend fun testEnvironment(
        params: JvmEnvironmentParams,
        timeout: Duration = defaultRequestTimeout,
    ): JvmEnvironmentResult = caller.request(BspMethods.BUILD_TARGET_JVM_TEST_ENVIRONMENT, params, timeout)

    /** `buildTarget/jvmCompileClasspath`. */
    public suspend fun compileClasspath(
        params: JvmCompileClasspathParams,
        timeout: Duration = defaultRequestTimeout,
    ): JvmCompileClasspathResult = caller.request(BspMethods.BUILD_TARGET_JVM_COMPILE_CLASSPATH, params, timeout)
}

/** Java extension requests. */
public class JavaProxy internal constructor(
    private val caller: RpcCaller,
) {
    /** `buildTarget/javacOptions`. */
    public suspend fun javacOptions(
        params: JavacOptionsParams,
        timeout: Duration = defaultRequestTimeout,
    ): JavacOptionsResult = caller.request(BspMethods.BUILD_TARGET_JAVAC_OPTIONS, params, timeout)
}

/** Scala extension requests. */
public class ScalaProxy internal constructor(
    private val caller: RpcCaller,
) {
    /** `buildTarget/scalacOptions`. */
    public suspend fun scalacOptions(
        params: ScalacOptionsParams,
        timeout: Duration = defaultRequestTimeout,
    ): ScalacOptionsResult = caller.request(BspMethods.BUILD_TARGET_SCALAC_OPTIONS, params, timeout)

    /** `buildTarget/scalaTestClasses`. */
    public suspend fun scalaTestClasses(
        params: ScalaTestClassesParams,
        timeout: Duration = defaultRequestTimeout,
    ): ScalaTestClassesResult = caller.request(BspMethods.BUILD_TARGET_SCALA_TEST_CLASSES, params, timeout)

    /** `buildTarget/scalaMainClasses`. */
    public suspend fun scalaMainClasses(
        params: ScalaMainClassesParams,
        timeout: Duration = defaultRequestTimeout,
    ): ScalaMainClassesResult = caller.request(BspMethods.BUILD_TARGET_SCALA_MAIN_CLASSES, params, timeout)
}

/** Python extension requests. */
public class PythonProxy internal constructor(
    private val caller: RpcCaller,
) {
    /** `buildTarget/pythonOptions`. */
    public suspend fun pythonOptions(
        params: PythonOptionsParams,
        timeout: Duration = defaultRequestTimeout,
    ): PythonOptionsResult = caller.request(BspMethods.BUILD_TARGET_PYTHON_OPTIONS, params, timeout)
}

/** Rust extension requests. */
public class RustProxy internal constructor(
    private val caller: RpcCaller,
) {
    /** `buildTarget/rustWorkspace`. */
    public suspend fun rustWorkspace(
        params: RustWorkspaceParams,
        timeout: Duration = defaultRequestTimeout,
    ): RustWorkspaceResult = caller.request(BspMethods.BUILD_TARGET_RUST_WORKSPACE, params, timeout)
}

/** Cargo extension requests. */
public class CargoProxy internal constructor(
    private val caller: RpcCaller,
) {
    /** `workspace/cargoFeaturesState`. */
    public suspend fun featuresState(timeout: Duration = defaultRequestTimeout): CargoFeaturesStateResult =
        caller.requestNoParams(BspMethods.WORKSPACE_CARGO_FEATURES_STATE, timeout)

    /** `workspace/setCargoFeatures`. */
    public suspend fun setFeatures(
        params: SetCargoFeaturesParams,
        timeout: Duration = defaultRequestTimeout,
    ): SetCargoFeaturesResult = caller.request(BspMethods.WORKSPACE_SET_CARGO_FEATURES, params, timeout)
}

/** C++ extension requests. */
public class CppProxy internal constructor(
    private val caller: RpcCaller,
) {
    /** `buildTarget/cppOptions`. */
    public suspend fun cppOptions(
        params: CppOptionsParams,
        timeout: Duration = defaultRequestTimeout,
    ): CppOptionsResult = caller.request(BspMethods.BUILD_TARGET_CPP_OPTIONS, params, timeout)
}
