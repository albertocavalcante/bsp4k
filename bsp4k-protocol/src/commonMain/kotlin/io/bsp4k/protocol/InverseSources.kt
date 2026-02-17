package io.bsp4k.protocol

import kotlinx.serialization.Serializable

/** Parameters for the `buildTarget/inverseSources` request. */
@Serializable
public data class InverseSourcesParams(
    val textDocument: TextDocumentIdentifier,
)

/** Result of the `buildTarget/inverseSources` request. */
@Serializable
public data class InverseSourcesResult(
    val targets: List<BuildTargetIdentifier>,
)

/** Identifies a text document by its URI. */
@Serializable
public data class TextDocumentIdentifier(
    val uri: URI,
)
