package io.bsp4k.protocol

import kotlinx.serialization.Serializable

/** Capabilities of the BSP server. Members default to `false` (BSP 2.2.0). */
@Serializable
public data class BuildServerCapabilities(
    val compileProvider: CompileProvider? = null,
    val testProvider: TestProvider? = null,
    val runProvider: RunProvider? = null,
    val debugProvider: DebugProvider? = null,
    val buildTargetDestinationsProvider: Boolean = false,
    val inverseSourcesProvider: Boolean = false,
    val dependencySourcesProvider: Boolean = false,
    val dependencyModulesProvider: Boolean = false,
    val resourcesProvider: Boolean = false,
    val outputPathsProvider: Boolean = false,
    val buildTargetChangedProvider: Boolean = false,
    val jvmRunEnvironmentProvider: Boolean = false,
    val jvmTestEnvironmentProvider: Boolean = false,
    val cargoFeaturesProvider: Boolean = false,
    val canReload: Boolean = false,
    val jvmCompileClasspathProvider: Boolean = false,
)

/** Capabilities of the BSP client. Receiver members default to `false` (BSP 2.2.0). */
@Serializable
public data class BuildClientCapabilities(
    val languageIds: List<String>,
    val buildTargetDestinationsReceiver: Boolean = false,
    val jvmCompileClasspathReceiver: Boolean = false,
)

/** Describes a compile provider with supported language IDs. */
@Serializable
public data class CompileProvider(
    val languageIds: List<String>,
)

/** Describes a test provider with supported language IDs. */
@Serializable
public data class TestProvider(
    val languageIds: List<String>,
)

/** Describes a run provider with supported language IDs. */
@Serializable
public data class RunProvider(
    val languageIds: List<String>,
)

/** Describes a debug provider with supported language IDs. */
@Serializable
public data class DebugProvider(
    val languageIds: List<String>,
)
