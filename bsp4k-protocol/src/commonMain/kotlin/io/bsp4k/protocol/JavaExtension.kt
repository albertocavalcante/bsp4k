package io.bsp4k.protocol

import kotlinx.serialization.Serializable

/** Parameters for the `buildTarget/javacOptions` request. */
@Serializable
public data class JavacOptionsParams(
    val targets: List<BuildTargetIdentifier>,
)

/** Result of the `buildTarget/javacOptions` request. */
@Serializable
public data class JavacOptionsResult(
    val items: List<JavacOptionsItem>,
)

/**
 * Javac options for a single build target.
 *
 * [classpath] is deprecated in BSP: when the jvmCompileClasspath capability is supported it may be
 * empty and `buildTarget/jvmCompileClasspath` should be used instead.
 */
@Serializable
public data class JavacOptionsItem(
    val target: BuildTargetIdentifier,
    val options: List<String>,
    val classpath: List<String>,
    val classDirectory: String,
)
