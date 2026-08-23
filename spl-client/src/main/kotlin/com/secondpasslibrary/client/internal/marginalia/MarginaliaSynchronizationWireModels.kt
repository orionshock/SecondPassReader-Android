package com.secondpasslibrary.client.internal.marginalia

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ReadingProgressResponseWire(val progress: ReadingProgressWire?)

@Serializable
internal data class MarginaliaAnnotationCollectionWire(
    val annotations: List<MarginaliaAnnotationWire>?
)

@Serializable
internal data class MarginaliaAnnotationBatchWire(
    val operations: List<MarginaliaAnnotationOperationWire>
)

@Serializable
internal data class MarginaliaAnnotationOperationWire(
    val action: String,
    @SerialName("client_id") val clientId: String? = null,
    val annotation: MarginaliaAnnotationDraftWire? = null
)

@Serializable
internal data class MarginaliaAnnotationDraftWire(
    @SerialName("client_id") val clientId: String,
    val kind: String,
    val location: MarginaliaAnnotationLocationWire,
    val body: MarginaliaHighlightBodyWire? = null
)
