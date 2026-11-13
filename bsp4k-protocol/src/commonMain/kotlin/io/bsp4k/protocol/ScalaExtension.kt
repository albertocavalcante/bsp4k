package io.bsp4k.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Scala-specific build target metadata (`dataKind` is [ScalaDataKinds.BUILD_TARGET]). */
@Serializable
public data class ScalaBuildTarget(
    val scalaOrganization: String,
    val scalaVersion: String,
    val scalaBinaryVersion: String,
    val platform: ScalaPlatform,
    val jars: List<URI>,
    val jvmBuildTarget: JvmBuildTarget? = null,
)

/** Scala platform of a build target. */
@Serializable(with = ScalaPlatformSerializer::class)
public enum class ScalaPlatform(
    public val value: Int,
) {
    Jvm(1),
    Js(2),
    Native(3),
}

/** Serializes [ScalaPlatform] as its integer value. */
internal object ScalaPlatformSerializer : IntEnumSerializer<ScalaPlatform>(
    "ScalaPlatform",
    ScalaPlatform.entries,
    ScalaPlatform::value,
)

/** Test parameters (`dataKind` is [ScalaDataKinds.TEST_PARAMS]). */
@Serializable
public data class ScalaTestParams(
    val testClasses: List<ScalaTestClassesItem>? = null,
    val jvmOptions: List<String>? = null,
)

/** Parameters for the `buildTarget/scalacOptions` request. */
@Serializable
public data class ScalacOptionsParams(
    val targets: List<BuildTargetIdentifier>,
)

/** Result of the `buildTarget/scalacOptions` request. */
@Serializable
public data class ScalacOptionsResult(
    val items: List<ScalacOptionsItem>,
)

/**
 * Scalac options for a single build target.
 *
 * [classpath] is deprecated in BSP: when the jvmCompileClasspath capability is supported it may be
 * empty and `buildTarget/jvmCompileClasspath` should be used instead.
 */
@Serializable
public data class ScalacOptionsItem(
    val target: BuildTargetIdentifier,
    val options: List<String>,
    val classpath: List<String>,
    val classDirectory: String,
)

/** Parameters for the `buildTarget/scalaTestClasses` request. Deprecated in BSP: use `buildTarget/jvmTestEnvironment`. */
@Serializable
public data class ScalaTestClassesParams(
    val targets: List<BuildTargetIdentifier>,
    val originId: String? = null,
)

/** Result of the `buildTarget/scalaTestClasses` request. Deprecated in BSP. */
@Serializable
public data class ScalaTestClassesResult(
    val items: List<ScalaTestClassesItem>,
)

/** Test classes of a single build target. */
@Serializable
public data class ScalaTestClassesItem(
    val target: BuildTargetIdentifier,
    val framework: String? = null,
    val classes: List<String>,
)

/** Parameters for the `buildTarget/scalaMainClasses` request. Deprecated in BSP: use `buildTarget/jvmRunEnvironment`. */
@Serializable
public data class ScalaMainClassesParams(
    val targets: List<BuildTargetIdentifier>,
    val originId: String? = null,
)

/** Result of the `buildTarget/scalaMainClasses` request. Deprecated in BSP. */
@Serializable
public data class ScalaMainClassesResult(
    val items: List<ScalaMainClassesItem>,
    val originId: String? = null,
)

/** Main classes of a single build target. */
@Serializable
public data class ScalaMainClassesItem(
    val target: BuildTargetIdentifier,
    val classes: List<ScalaMainClass>,
)

/**
 * A Scala main class (`dataKind` is [ScalaDataKinds.MAIN_CLASS]).
 *
 * [arguments] and [environmentVariables] are deprecated in BSP 2.2.0: use `buildTarget/run` params.
 */
@Serializable
public data class ScalaMainClass(
    @SerialName("class") val className: String,
    val arguments: List<String>,
    val jvmOptions: List<String>,
    val environmentVariables: List<String>? = null,
)

/** Marker payload to attach to a remote debuggee (`dataKind` is [ScalaDataKinds.ATTACH_REMOTE]). */
@Serializable
public data object ScalaAttachRemote

/**
 * Test suite selections (`dataKind` is [ScalaDataKinds.TEST_SUITES_SELECTION]).
 *
 * [environmentVariables] is deprecated in BSP 2.2.0: use `buildTarget/test` params.
 */
@Serializable
public data class ScalaTestSuites(
    val suites: List<ScalaTestSuiteSelection>,
    val jvmOptions: List<String>,
    val environmentVariables: List<String>,
)

/** A test class and the tests to run in it. */
@Serializable
public data class ScalaTestSuiteSelection(
    val className: String,
    val tests: List<String>,
)

/** Scala-specific diagnostic data (`dataKind` is [ScalaDataKinds.DIAGNOSTIC]). */
@Serializable
public data class ScalaDiagnostic(
    val actions: List<ScalaAction>? = null,
)

/** A suggested action for a diagnostic. */
@Serializable
public data class ScalaAction(
    val title: String,
    val description: String? = null,
    val edit: ScalaWorkspaceEdit? = null,
)

/** A set of text edits. */
@Serializable
public data class ScalaWorkspaceEdit(
    val changes: List<ScalaTextEdit>,
)

/** A single text edit. */
@Serializable
public data class ScalaTextEdit(
    val range: Range,
    val newText: String,
)

/**
 * `dataKind` strings defined by the Scala extension.
 *
 * [TEST_SUITES] has no dedicated shape: its payload is a plain list of class names.
 */
public object ScalaDataKinds {
    /** `BuildTarget.dataKind` for [ScalaBuildTarget]. */
    public const val BUILD_TARGET: String = "scala"

    /** `Diagnostic.dataKind` for [ScalaDiagnostic]. */
    public const val DIAGNOSTIC: String = "scala"

    /** `TestParams.dataKind` for [ScalaTestParams]. */
    public const val TEST_PARAMS: String = "scala-test"

    /** `TestParams.dataKind` for a `List<String>` of test class names. */
    public const val TEST_SUITES: String = "scala-test-suites"

    /** `TestParams.dataKind` for [ScalaTestSuites]. */
    public const val TEST_SUITES_SELECTION: String = "scala-test-suites-selection"

    /** `RunParams.dataKind` and `DebugSessionParams.dataKind` for [ScalaMainClass]. */
    public const val MAIN_CLASS: String = "scala-main-class"

    /** `DebugSessionParams.dataKind` for [ScalaAttachRemote]. */
    public const val ATTACH_REMOTE: String = "scala-attach-remote"
}
