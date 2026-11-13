package io.bsp4k.protocol

import kotlinx.serialization.Serializable

/** Cargo-specific build target data; carried in [BuildTarget.data] with `dataKind` [KIND]. */
@Serializable
public data class CargoBuildTarget(
    /** A [RustEdition] value; other values are preserved. */
    val edition: String,
    val requiredFeatures: Set<String>,
) {
    public companion object {
        /** The `dataKind` of build target data of this shape. */
        public const val KIND: String = "cargo"
    }
}

/** Unstable in BSP 2.2. Result of `workspace/cargoFeaturesState`; the request has no parameters. */
@Serializable
public data class CargoFeaturesStateResult(
    val packagesFeatures: List<PackageFeatures>,
)

/** Cargo features of one package. */
@Serializable
public data class PackageFeatures(
    val packageId: String,
    val targets: List<BuildTargetIdentifier>,
    val availableFeatures: Map<String, Set<String>>,
    val enabledFeatures: Set<String>,
)

/** Unstable in BSP 2.2. Parameters of `workspace/setCargoFeatures`. */
@Serializable
public data class SetCargoFeaturesParams(
    val packageId: String,
    val features: Set<String>,
)

/** Unstable in BSP 2.2. Result of `workspace/setCargoFeatures`. */
@Serializable
public data class SetCargoFeaturesResult(
    val statusCode: StatusCode,
)
