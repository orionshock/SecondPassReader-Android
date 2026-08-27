package com.secondpasslibrary.reader.reader.marginalia

import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus

internal data class ReaderMarginaliaLayerSummary(
    val sessionId: String,
    val role: ReaderMarginaliaLayerRole,
    val sessionStatus: ReaderSessionStatus,
    val sessionName: String?,
    val startedAt: String?,
    val closedAt: String?,
    val lastActivityAt: String?,
    val annotationCount: Int?
)

internal enum class ReaderMarginaliaLayerRole {
    CURRENT,
    PREVIOUS
}

internal data class ReaderPreviousMarginaliaLayer(
    val summary: ReaderMarginaliaLayerSummary,
    val loadState: ReaderMarginaliaLayerLoadState = ReaderMarginaliaLayerLoadState.NOT_LOADED,
    val visibility: ReaderMarginaliaLayerVisibility = ReaderMarginaliaLayerVisibility.HIDDEN
) {
    init {
        require(summary.role == ReaderMarginaliaLayerRole.PREVIOUS) {
            "Previous marginalia layer must have the PREVIOUS role."
        }
    }
}

internal enum class ReaderMarginaliaLayerLoadState {
    NOT_LOADED,
    LOADING,
    LOADED,
    FAILED
}

internal enum class ReaderMarginaliaLayerVisibility {
    HIDDEN,
    VISIBLE
}

internal fun ReaderSessionContext.toCurrentMarginaliaLayer() = ReaderMarginaliaLayerSummary(
    sessionId = sessionId,
    role = ReaderMarginaliaLayerRole.CURRENT,
    sessionStatus = status,
    sessionName = sessionName,
    startedAt = startedAt,
    closedAt = closedAt,
    lastActivityAt = lastActivityAt,
    annotationCount = annotationCount
)

internal fun ReaderMarginaliaLayerSummary.isWritable(): Boolean =
    role == ReaderMarginaliaLayerRole.CURRENT && sessionStatus == ReaderSessionStatus.ACTIVE
