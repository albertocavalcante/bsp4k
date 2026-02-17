package io.bsp4k.protocol

import kotlinx.serialization.Serializable

/** JVM-specific build target data. */
@Serializable
public data class JvmBuildTarget(
    val javaHome: URI? = null,
    val javaVersion: String? = null,
)

/** Parameters for JVM environment requests. */
@Serializable
public data class JvmEnvironmentParams(
    val targets: List<BuildTargetIdentifier>,
    val originId: String? = null,
)

/** Result of JVM environment requests. */
@Serializable
public data class JvmEnvironmentResult(
    val items: List<JvmEnvironmentItem>,
)

/** JVM environment for a single build target. */
@Serializable
public data class JvmEnvironmentItem(
    val target: BuildTargetIdentifier,
    val classpath: List<URI>,
    val jvmOptions: List<String>,
    val workingDirectory: String,
    val environmentVariables: Map<String, String>,
    val mainClasses: List<JvmMainClass>? = null,
)

/** A JVM main class. */
@Serializable
public data class JvmMainClass(
    val className: String,
    val arguments: List<String>,
)

/** Parameters for JVM compile classpath requests. */
@Serializable
public data class JvmCompileClasspathParams(
    val targets: List<BuildTargetIdentifier>,
)

/** Result of JVM compile classpath requests. */
@Serializable
public data class JvmCompileClasspathResult(
    val items: List<JvmCompileClasspathItem>,
)

/** JVM compile classpath for a single build target. */
@Serializable
public data class JvmCompileClasspathItem(
    val target: BuildTargetIdentifier,
    val classpath: List<URI>,
)
