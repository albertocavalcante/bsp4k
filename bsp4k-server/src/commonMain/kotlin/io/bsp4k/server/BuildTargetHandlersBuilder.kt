package io.bsp4k.server

import io.bsp4k.protocol.BspMethods
import io.bsp4k.protocol.CleanCacheParams
import io.bsp4k.protocol.CleanCacheResult
import io.bsp4k.protocol.CompileParams
import io.bsp4k.protocol.CompileResult
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
import io.bsp4k.protocol.ResourcesParams
import io.bsp4k.protocol.ResourcesResult
import io.bsp4k.protocol.RunParams
import io.bsp4k.protocol.RunResult
import io.bsp4k.protocol.SourcesParams
import io.bsp4k.protocol.SourcesResult
import io.bsp4k.protocol.TestParams
import io.bsp4k.protocol.TestResult

/**
 * Registers handlers for the core `buildTarget/` requests. Extension requests in that namespace, such as
 * `buildTarget/jvmRunEnvironment`, live in their extension builders.
 */
@BspServerDsl
public class BuildTargetHandlersBuilder internal constructor(
    private val registry: HandlerRegistry,
) {
    /** Handles `buildTarget/sources`. */
    public fun sources(handler: suspend BuildServerContext.(SourcesParams) -> SourcesResult) {
        registry.handleRequest(BspMethods.BUILD_TARGET_SOURCES, handler)
    }

    /** Handles `buildTarget/inverseSources`; requires `inverseSourcesProvider`. */
    public fun inverseSources(handler: suspend BuildServerContext.(InverseSourcesParams) -> InverseSourcesResult) {
        registry.handleRequest(BspMethods.BUILD_TARGET_INVERSE_SOURCES, handler)
    }

    /** Handles `buildTarget/dependencySources`; requires `dependencySourcesProvider`. */
    public fun dependencySources(handler: suspend BuildServerContext.(DependencySourcesParams) -> DependencySourcesResult) {
        registry.handleRequest(BspMethods.BUILD_TARGET_DEPENDENCY_SOURCES, handler)
    }

    /** Handles `buildTarget/dependencyModules`; requires `dependencyModulesProvider`. */
    public fun dependencyModules(handler: suspend BuildServerContext.(DependencyModulesParams) -> DependencyModulesResult) {
        registry.handleRequest(BspMethods.BUILD_TARGET_DEPENDENCY_MODULES, handler)
    }

    /** Handles `buildTarget/resources`; requires `resourcesProvider`. */
    public fun resources(handler: suspend BuildServerContext.(ResourcesParams) -> ResourcesResult) {
        registry.handleRequest(BspMethods.BUILD_TARGET_RESOURCES, handler)
    }

    /** Handles `buildTarget/outputPaths`; requires `outputPathsProvider`. */
    public fun outputPaths(handler: suspend BuildServerContext.(OutputPathsParams) -> OutputPathsResult) {
        registry.handleRequest(BspMethods.BUILD_TARGET_OUTPUT_PATHS, handler)
    }

    /** Handles `buildTarget/destinations`; requires `buildTargetDestinationsProvider`. */
    public fun destinations(handler: suspend BuildServerContext.(DestinationsParams) -> DestinationsResult) {
        registry.handleRequest(BspMethods.BUILD_TARGET_DESTINATIONS, handler)
    }

    /** Handles `buildTarget/compile`; requires `compileProvider`. Report progress with [BuildClientProxy.withTask]. */
    public fun compile(handler: suspend BuildServerContext.(CompileParams) -> CompileResult) {
        registry.handleRequest(BspMethods.BUILD_TARGET_COMPILE, handler)
    }

    /** Handles `buildTarget/test`; requires `testProvider`. */
    public fun test(handler: suspend BuildServerContext.(TestParams) -> TestResult) {
        registry.handleRequest(BspMethods.BUILD_TARGET_TEST, handler)
    }

    /**
     * Handles `buildTarget/run`; requires `runProvider`. Forward process output with
     * [BuildClientProxy.printStdout] and [BuildClientProxy.printStderr]. BSP asks a cancelled run to kill
     * its process: in a bounded session `$/cancelRequest` cancels this handler's coroutine.
     */
    public fun run(handler: suspend BuildServerContext.(RunParams) -> RunResult) {
        registry.handleRequest(BspMethods.BUILD_TARGET_RUN, handler)
    }

    /** Handles `buildTarget/cleanCache`. */
    public fun cleanCache(handler: suspend BuildServerContext.(CleanCacheParams) -> CleanCacheResult) {
        registry.handleRequest(BspMethods.BUILD_TARGET_CLEAN_CACHE, handler)
    }
}
