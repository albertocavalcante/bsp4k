package io.bsp4k.client

import io.bsp4k.protocol.BspMethods
import io.bsp4k.protocol.CleanCacheParams
import io.bsp4k.protocol.CleanCacheResult
import io.bsp4k.protocol.CompileParams
import io.bsp4k.protocol.CompileResult
import io.bsp4k.protocol.DebugSessionAddress
import io.bsp4k.protocol.DebugSessionParams
import io.bsp4k.protocol.DependencyModulesParams
import io.bsp4k.protocol.DependencyModulesResult
import io.bsp4k.protocol.DependencySourcesParams
import io.bsp4k.protocol.DependencySourcesResult
import io.bsp4k.protocol.DestinationsParams
import io.bsp4k.protocol.DestinationsResult
import io.bsp4k.protocol.InverseSourcesParams
import io.bsp4k.protocol.InverseSourcesResult
import io.bsp4k.protocol.OutputPathsParams
import io.bsp4k.protocol.OutputPathsResult
import io.bsp4k.protocol.ReadParams
import io.bsp4k.protocol.ResourcesParams
import io.bsp4k.protocol.ResourcesResult
import io.bsp4k.protocol.RunParams
import io.bsp4k.protocol.RunResult
import io.bsp4k.protocol.SourcesParams
import io.bsp4k.protocol.SourcesResult
import io.bsp4k.protocol.TestParams
import io.bsp4k.protocol.TestResult
import io.bsp4k.protocol.WorkspaceBuildTargetsResult
import kotlin.time.Duration

/**
 * Typed calls for every client-to-server BSP core request and notification, plus one namespace
 * per extension. Every request takes a per-call [Duration] timeout. All calls require a
 * [SessionState.READY] session and otherwise throw [SessionStateException].
 */
public class BuildServerProxy internal constructor(
    private val caller: RpcCaller,
) {
    /** JVM extension requests. */
    public val jvm: JvmProxy = JvmProxy(caller)

    /** Java extension requests. */
    public val java: JavaProxy = JavaProxy(caller)

    /** Scala extension requests. */
    public val scala: ScalaProxy = ScalaProxy(caller)

    /** Python extension requests. */
    public val python: PythonProxy = PythonProxy(caller)

    /** Rust extension requests. */
    public val rust: RustProxy = RustProxy(caller)

    /** Cargo extension requests. */
    public val cargo: CargoProxy = CargoProxy(caller)

    /** C++ extension requests. */
    public val cpp: CppProxy = CppProxy(caller)

    /** `workspace/buildTargets`. */
    public suspend fun workspaceBuildTargets(timeout: Duration = defaultRequestTimeout): WorkspaceBuildTargetsResult =
        caller.requestNoParams(BspMethods.WORKSPACE_BUILD_TARGETS, timeout)

    /** `workspace/reload`. */
    public suspend fun workspaceReload(timeout: Duration = defaultRequestTimeout) {
        caller.callUnit(BspMethods.WORKSPACE_RELOAD, null, timeout)
    }

    /** `buildTarget/destinations`. */
    public suspend fun destinations(
        params: DestinationsParams,
        timeout: Duration = defaultRequestTimeout,
    ): DestinationsResult = caller.request(BspMethods.BUILD_TARGET_DESTINATIONS, params, timeout)

    /** `buildTarget/sources`. */
    public suspend fun sources(
        params: SourcesParams,
        timeout: Duration = defaultRequestTimeout,
    ): SourcesResult = caller.request(BspMethods.BUILD_TARGET_SOURCES, params, timeout)

    /** `buildTarget/inverseSources`. */
    public suspend fun inverseSources(
        params: InverseSourcesParams,
        timeout: Duration = defaultRequestTimeout,
    ): InverseSourcesResult = caller.request(BspMethods.BUILD_TARGET_INVERSE_SOURCES, params, timeout)

    /** `buildTarget/dependencySources`. */
    public suspend fun dependencySources(
        params: DependencySourcesParams,
        timeout: Duration = defaultRequestTimeout,
    ): DependencySourcesResult = caller.request(BspMethods.BUILD_TARGET_DEPENDENCY_SOURCES, params, timeout)

    /** `buildTarget/dependencyModules`. */
    public suspend fun dependencyModules(
        params: DependencyModulesParams,
        timeout: Duration = defaultRequestTimeout,
    ): DependencyModulesResult = caller.request(BspMethods.BUILD_TARGET_DEPENDENCY_MODULES, params, timeout)

    /** `buildTarget/resources`. */
    public suspend fun resources(
        params: ResourcesParams,
        timeout: Duration = defaultRequestTimeout,
    ): ResourcesResult = caller.request(BspMethods.BUILD_TARGET_RESOURCES, params, timeout)

    /** `buildTarget/outputPaths`. */
    public suspend fun outputPaths(
        params: OutputPathsParams,
        timeout: Duration = defaultRequestTimeout,
    ): OutputPathsResult = caller.request(BspMethods.BUILD_TARGET_OUTPUT_PATHS, params, timeout)

    /** `buildTarget/compile`. */
    public suspend fun compile(
        params: CompileParams,
        timeout: Duration = defaultRequestTimeout,
    ): CompileResult = caller.request(BspMethods.BUILD_TARGET_COMPILE, params, timeout)

    /** `buildTarget/test`. */
    public suspend fun test(
        params: TestParams,
        timeout: Duration = defaultRequestTimeout,
    ): TestResult = caller.request(BspMethods.BUILD_TARGET_TEST, params, timeout)

    /** `buildTarget/run`. */
    public suspend fun run(
        params: RunParams,
        timeout: Duration = defaultRequestTimeout,
    ): RunResult = caller.request(BspMethods.BUILD_TARGET_RUN, params, timeout)

    /** `buildTarget/cleanCache`. */
    public suspend fun cleanCache(
        params: CleanCacheParams,
        timeout: Duration = defaultRequestTimeout,
    ): CleanCacheResult = caller.request(BspMethods.BUILD_TARGET_CLEAN_CACHE, params, timeout)

    /** `debugSession/start`. */
    public suspend fun debugSession(
        params: DebugSessionParams,
        timeout: Duration = defaultRequestTimeout,
    ): DebugSessionAddress = caller.request(BspMethods.DEBUG_SESSION_START, params, timeout)

    /** `run/readStdin` notification: forwards console input to a running target. */
    public suspend fun readStdin(params: ReadParams) {
        caller.notification(BspMethods.RUN_READ_STDIN, params)
    }
}
