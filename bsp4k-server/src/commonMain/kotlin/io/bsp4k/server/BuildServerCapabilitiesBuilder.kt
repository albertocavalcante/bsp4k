package io.bsp4k.server

import io.bsp4k.protocol.BuildServerCapabilities
import io.bsp4k.protocol.CompileProvider
import io.bsp4k.protocol.DebugProvider
import io.bsp4k.protocol.RunProvider
import io.bsp4k.protocol.TestProvider

/**
 * Builder for constructing [BuildServerCapabilities] advertised during initialization.
 *
 * Each provider names the handler it requires; [BuildServerConfig] rejects a provider without that handler
 * and the handler without its provider.
 */
@BspServerDsl
public class BuildServerCapabilitiesBuilder internal constructor() {
    /** Languages served by `buildTarget/compile`; requires `buildTarget { compile }`. */
    public var compileProvider: CompileProvider? = null

    /** Languages served by `buildTarget/test`; requires `buildTarget { test }`. */
    public var testProvider: TestProvider? = null

    /** Languages served by `buildTarget/run`; requires `buildTarget { run }`. */
    public var runProvider: RunProvider? = null

    /** Languages served by `debugSession/start`; requires `debugSession { start }`. */
    public var debugProvider: DebugProvider? = null

    /** Whether the server supports `buildTarget/destinations`; requires `buildTarget { destinations }`. */
    public var buildTargetDestinationsProvider: Boolean = false

    /** Whether the server supports `buildTarget/inverseSources`; requires `buildTarget { inverseSources }`. */
    public var inverseSourcesProvider: Boolean = false

    /** Whether the server supports `buildTarget/dependencySources`; requires `buildTarget { dependencySources }`. */
    public var dependencySourcesProvider: Boolean = false

    /** Whether the server supports `buildTarget/dependencyModules`; requires `buildTarget { dependencyModules }`. */
    public var dependencyModulesProvider: Boolean = false

    /** Whether the server supports `buildTarget/resources`; requires `buildTarget { resources }`. */
    public var resourcesProvider: Boolean = false

    /** Whether the server supports `buildTarget/outputPaths`; requires `buildTarget { outputPaths }`. */
    public var outputPathsProvider: Boolean = false

    /** Whether the server sends `buildTarget/didChange` through [BuildClientProxy.buildTargetDidChange]. */
    public var buildTargetChangedProvider: Boolean = false

    /** Whether the server supports `buildTarget/jvmRunEnvironment`; requires `jvm { runEnvironment }`. */
    public var jvmRunEnvironmentProvider: Boolean = false

    /** Whether the server supports `buildTarget/jvmTestEnvironment`; requires `jvm { testEnvironment }`. */
    public var jvmTestEnvironmentProvider: Boolean = false

    /** Whether the server supports the Cargo features methods; requires `cargo { featuresState; setFeatures }`. */
    public var cargoFeaturesProvider: Boolean = false

    /** Whether the server supports `workspace/reload`; requires `workspace { reload }`. */
    public var canReload: Boolean = false

    /** Whether the server supports `buildTarget/jvmCompileClasspath`; requires `jvm { compileClasspath }`. */
    public var jvmCompileClasspathProvider: Boolean = false

    internal fun build(): BuildServerCapabilities =
        BuildServerCapabilities(
            compileProvider = compileProvider,
            testProvider = testProvider,
            runProvider = runProvider,
            debugProvider = debugProvider,
            buildTargetDestinationsProvider = buildTargetDestinationsProvider,
            inverseSourcesProvider = inverseSourcesProvider,
            dependencySourcesProvider = dependencySourcesProvider,
            dependencyModulesProvider = dependencyModulesProvider,
            resourcesProvider = resourcesProvider,
            outputPathsProvider = outputPathsProvider,
            buildTargetChangedProvider = buildTargetChangedProvider,
            jvmRunEnvironmentProvider = jvmRunEnvironmentProvider,
            jvmTestEnvironmentProvider = jvmTestEnvironmentProvider,
            cargoFeaturesProvider = cargoFeaturesProvider,
            canReload = canReload,
            jvmCompileClasspathProvider = jvmCompileClasspathProvider,
        )
}
