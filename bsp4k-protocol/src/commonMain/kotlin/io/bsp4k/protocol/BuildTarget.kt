package io.bsp4k.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** A build target with its metadata. */
@Serializable
public data class BuildTarget(
    val id: BuildTargetIdentifier,
    val displayName: String? = null,
    val baseDirectory: URI? = null,
    val tags: List<String>,
    val capabilities: BuildTargetCapabilities,
    val languageIds: List<String>,
    val dependencies: List<BuildTargetIdentifier>,
    override val dataKind: String? = null,
    override val data: JsonElement? = null,
) : BspDataCarrier

/** Capabilities of a build target. */
@Serializable
public data class BuildTargetCapabilities(
    val canCompile: Boolean = false,
    val canTest: Boolean = false,
    val canRun: Boolean = false,
    val canDebug: Boolean = false,
)

/** Predefined build target tags (open enum: `BuildTarget.tags` keeps unknown values). */
public object BuildTargetTag {
    /** Target contains re-usable functionality for downstream targets. */
    public const val LIBRARY: String = "library"

    /** Target contains source code for producing any kind of application. */
    public const val APPLICATION: String = "application"

    /** Target contains source code for testing purposes. */
    public const val TEST: String = "test"

    /** Target contains source code for integration testing purposes. */
    public const val INTEGRATION_TEST: String = "integration-test"

    /** Target contains source code to measure performance of a program. */
    public const val BENCHMARK: String = "benchmark"

    /** Target should be ignored by IDEs. */
    public const val NO_IDE: String = "no-ide"

    /** Actions on the target should only be invoked manually and explicitly. */
    public const val MANUAL: String = "manual"

    /** All values defined by BSP 2.2.0. */
    public val entries: List<String> = listOf(LIBRARY, APPLICATION, TEST, INTEGRATION_TEST, BENCHMARK, NO_IDE, MANUAL)
}
