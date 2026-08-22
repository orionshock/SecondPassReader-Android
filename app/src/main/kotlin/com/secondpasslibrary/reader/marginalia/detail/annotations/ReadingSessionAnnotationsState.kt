package com.secondpasslibrary.reader.marginalia.detail.annotations

import com.secondpasslibrary.client.MarginaliaAnnotation
import com.secondpasslibrary.reader.marginalia.MarginaliaFailure

internal data class ReadingSessionAnnotationsState(
    val sessionId: String? = null,
    val annotations: List<MarginaliaAnnotation> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val failure: MarginaliaFailure? = null
)
