package io.bsp4k.protocol

import kotlinx.serialization.Serializable

/** Unstable in BSP 2.2. Parameters of `buildTarget/rustWorkspace`. */
@Serializable
public data class RustWorkspaceParams(
    val targets: List<BuildTargetIdentifier>,
)

/** Unstable in BSP 2.2. Result of `buildTarget/rustWorkspace`. */
@Serializable
public data class RustWorkspaceResult(
    val packages: List<RustPackage>,
    val rawDependencies: Map<String, List<RustRawDependency>>,
    val dependencies: Map<String, List<RustDependency>>,
    val resolvedTargets: List<BuildTargetIdentifier>,
)

/** A Rust package (crate) of the workspace or one of its dependencies. */
@Serializable
public data class RustPackage(
    val id: String,
    val rootUrl: URI,
    val name: String,
    val version: String,
    /** A [RustPackageOrigin] value; other values are preserved. */
    val origin: String,
    /** A [RustEdition] value; other values are preserved. */
    val edition: String,
    val source: String? = null,
    val resolvedTargets: List<RustTarget>,
    val allTargets: List<RustTarget>,
    val features: Map<String, Set<String>>,
    val enabledFeatures: Set<String>,
    val cfgOptions: Map<String, List<String>>? = null,
    val env: Map<String, String>? = null,
    val outDirUrl: URI? = null,
    val procMacroArtifact: URI? = null,
)

/** A Rust target (a crate root) of a package. */
@Serializable
public data class RustTarget(
    val name: String,
    val crateRootUrl: URI,
    val kind: RustTargetKind,
    val crateTypes: List<RustCrateType>? = null,
    /** A [RustEdition] value; other values are preserved. */
    val edition: String,
    val doctest: Boolean,
    val requiredFeatures: Set<String>? = null,
)

/** A dependency as declared in a package manifest. */
@Serializable
public data class RustRawDependency(
    val name: String,
    val rename: String? = null,
    /** A [RustDepKind] value; other values are preserved. */
    val kind: String? = null,
    val target: String? = null,
    val optional: Boolean,
    val usesDefaultFeatures: Boolean,
    val features: Set<String>,
)

/** A resolved dependency of a package. */
@Serializable
public data class RustDependency(
    val pkg: String,
    val name: String? = null,
    val depKinds: List<RustDepKindInfo>? = null,
)

/** The kind and optional target platform of a resolved dependency. */
@Serializable
public data class RustDepKindInfo(
    /** A [RustDepKind] value; other values are preserved. */
    val kind: String,
    val target: String? = null,
)

/** Crate types of a Rust target. */
@Serializable(with = RustCrateTypeSerializer::class)
public enum class RustCrateType(
    public val value: Int,
) {
    Bin(1),
    Lib(2),
    Rlib(3),
    Dylib(4),
    Cdylib(5),
    Staticlib(6),
    ProcMacro(7),
    Unknown(8),
}

/** Serializes [RustCrateType] as its integer value. */
internal object RustCrateTypeSerializer : IntEnumSerializer<RustCrateType>(
    "RustCrateType",
    RustCrateType.entries,
    RustCrateType::value,
)

/** Kinds of Rust targets. */
@Serializable(with = RustTargetKindSerializer::class)
public enum class RustTargetKind(
    public val value: Int,
) {
    Lib(1),
    Bin(2),
    Test(3),
    Example(4),
    Bench(5),
    CustomBuild(6),
    Unknown(7),
}

/** Serializes [RustTargetKind] as its integer value. */
internal object RustTargetKindSerializer : IntEnumSerializer<RustTargetKind>(
    "RustTargetKind",
    RustTargetKind.entries,
    RustTargetKind::value,
)

/** Open enum: Rust editions; servers may send other values. */
public object RustEdition {
    public const val E2015: String = "2015"
    public const val E2018: String = "2018"
    public const val E2021: String = "2021"

    /** All values defined by BSP 2.2.0. */
    public val entries: List<String> = listOf(E2015, E2018, E2021)
}

/** Open enum: dependency kinds; servers may send other values. */
public object RustDepKind {
    public const val UNCLASSIFIED: String = "unclassified"
    public const val NORMAL: String = "normal"
    public const val DEV: String = "dev"
    public const val BUILD: String = "build"

    /** All values defined by BSP 2.2.0. */
    public val entries: List<String> = listOf(UNCLASSIFIED, NORMAL, DEV, BUILD)
}

/** Open enum: package origins; servers may send other values. */
public object RustPackageOrigin {
    public const val STDLIB: String = "stdlib"
    public const val WORKSPACE: String = "workspace"
    public const val DEPENDENCY: String = "dependency"
    public const val STDLIB_DEPENDENCY: String = "stdlib-dependency"

    /** All values defined by BSP 2.2.0. */
    public val entries: List<String> = listOf(STDLIB, WORKSPACE, DEPENDENCY, STDLIB_DEPENDENCY)
}
