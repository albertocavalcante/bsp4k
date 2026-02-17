package io.bsp4k.protocol

import kotlinx.serialization.Serializable

/** Capabilities of the BSP server. */
@Serializable
public data class BuildServerCapabilities(
    val compileProvider: CompileProvider? = null,
    val testProvider: TestProvider? = null,
    val runProvider: RunProvider? = null,
    val debugProvider: DebugProvider? = null,
    val inverseSourcesProvider: Boolean? = null,
    val dependencySourcesProvider: Boolean? = null,
    val dependencyModulesProvider: Boolean? = null,
    val resourcesProvider: Boolean? = null,
    val outputPathsProvider: Boolean? = null,
    val buildTargetChangedProvider: Boolean? = null,
    val jvmRunEnvironmentProvider: Boolean? = null,
    val jvmTestEnvironmentProvider: Boolean? = null,
    val jvmCompileClasspathProvider: Boolean? = null,
    val canReload: Boolean? = null,
)

/** Capabilities of the BSP client. */
@Serializable
public data class BuildClientCapabilities(
    val languageIds: List<String>,
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
