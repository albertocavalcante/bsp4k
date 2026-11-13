package io.bsp4k.server

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

// The sbt and Maven extensions define data kinds only, so they have no handler builders.

/** Registers handlers for the JVM extension. */
@BspServerDsl
public class JvmHandlersBuilder internal constructor(
    private val registry: HandlerRegistry,
) {
    /** Handles `buildTarget/jvmRunEnvironment`; requires `jvmRunEnvironmentProvider`. */
    public fun runEnvironment(handler: suspend BuildServerContext.(JvmEnvironmentParams) -> JvmEnvironmentResult) {
        registry.handleRequest(BspMethods.BUILD_TARGET_JVM_RUN_ENVIRONMENT, handler)
    }

    /** Handles `buildTarget/jvmTestEnvironment`; requires `jvmTestEnvironmentProvider`. */
    public fun testEnvironment(handler: suspend BuildServerContext.(JvmEnvironmentParams) -> JvmEnvironmentResult) {
        registry.handleRequest(BspMethods.BUILD_TARGET_JVM_TEST_ENVIRONMENT, handler)
    }

    /** Handles `buildTarget/jvmCompileClasspath`; requires `jvmCompileClasspathProvider`. */
    public fun compileClasspath(handler: suspend BuildServerContext.(JvmCompileClasspathParams) -> JvmCompileClasspathResult) {
        registry.handleRequest(BspMethods.BUILD_TARGET_JVM_COMPILE_CLASSPATH, handler)
    }
}

/** Registers handlers for the Java extension. */
@BspServerDsl
public class JavaHandlersBuilder internal constructor(
    private val registry: HandlerRegistry,
) {
    /** Handles `buildTarget/javacOptions`. */
    public fun javacOptions(handler: suspend BuildServerContext.(JavacOptionsParams) -> JavacOptionsResult) {
        registry.handleRequest(BspMethods.BUILD_TARGET_JAVAC_OPTIONS, handler)
    }
}

/** Registers handlers for the Scala extension. */
@BspServerDsl
public class ScalaHandlersBuilder internal constructor(
    private val registry: HandlerRegistry,
) {
    /** Handles `buildTarget/scalacOptions`. */
    public fun scalacOptions(handler: suspend BuildServerContext.(ScalacOptionsParams) -> ScalacOptionsResult) {
        registry.handleRequest(BspMethods.BUILD_TARGET_SCALAC_OPTIONS, handler)
    }

    /** Handles `buildTarget/scalaTestClasses`, which BSP deprecates in favour of `buildTarget/jvmTestEnvironment`. */
    public fun scalaTestClasses(handler: suspend BuildServerContext.(ScalaTestClassesParams) -> ScalaTestClassesResult) {
        registry.handleRequest(BspMethods.BUILD_TARGET_SCALA_TEST_CLASSES, handler)
    }

    /** Handles `buildTarget/scalaMainClasses`, which BSP deprecates in favour of `buildTarget/jvmRunEnvironment`. */
    public fun scalaMainClasses(handler: suspend BuildServerContext.(ScalaMainClassesParams) -> ScalaMainClassesResult) {
        registry.handleRequest(BspMethods.BUILD_TARGET_SCALA_MAIN_CLASSES, handler)
    }
}

/** Registers handlers for the Python extension. */
@BspServerDsl
public class PythonHandlersBuilder internal constructor(
    private val registry: HandlerRegistry,
) {
    /** Handles `buildTarget/pythonOptions`. */
    public fun pythonOptions(handler: suspend BuildServerContext.(PythonOptionsParams) -> PythonOptionsResult) {
        registry.handleRequest(BspMethods.BUILD_TARGET_PYTHON_OPTIONS, handler)
    }
}

/** Registers handlers for the Rust extension. */
@BspServerDsl
public class RustHandlersBuilder internal constructor(
    private val registry: HandlerRegistry,
) {
    /** Handles `buildTarget/rustWorkspace` (unstable in BSP 2.2). */
    public fun rustWorkspace(handler: suspend BuildServerContext.(RustWorkspaceParams) -> RustWorkspaceResult) {
        registry.handleRequest(BspMethods.BUILD_TARGET_RUST_WORKSPACE, handler)
    }
}

/** Registers handlers for the Cargo extension; both require `cargoFeaturesProvider`. */
@BspServerDsl
public class CargoHandlersBuilder internal constructor(
    private val registry: HandlerRegistry,
) {
    /** Handles `workspace/cargoFeaturesState` (unstable in BSP 2.2), which has no params. */
    public fun featuresState(handler: suspend BuildServerContext.() -> CargoFeaturesStateResult) {
        registry.handleParamless(BspMethods.WORKSPACE_CARGO_FEATURES_STATE, handler)
    }

    /** Handles `workspace/setCargoFeatures` (unstable in BSP 2.2). */
    public fun setFeatures(handler: suspend BuildServerContext.(SetCargoFeaturesParams) -> SetCargoFeaturesResult) {
        registry.handleRequest(BspMethods.WORKSPACE_SET_CARGO_FEATURES, handler)
    }
}

/** Registers handlers for the C++ extension. */
@BspServerDsl
public class CppHandlersBuilder internal constructor(
    private val registry: HandlerRegistry,
) {
    /** Handles `buildTarget/cppOptions`. */
    public fun cppOptions(handler: suspend BuildServerContext.(CppOptionsParams) -> CppOptionsResult) {
        registry.handleRequest(BspMethods.BUILD_TARGET_CPP_OPTIONS, handler)
    }
}
