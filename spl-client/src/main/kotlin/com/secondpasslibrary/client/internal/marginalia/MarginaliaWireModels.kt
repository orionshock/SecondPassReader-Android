package com.secondpasslibrary.client.internal.marginalia

import com.secondpasslibrary.client.internal.library.BookAuthorWire
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class MarginaliaBookPageWire(
    val count: Int? = null,
    val next: String? = null,
    val previous: String? = null,
    val results: List<MarginaliaBookWire>? = null
)

@Serializable
internal data class MarginaliaBookWire(
    val id: String? = null,
    val title: String? = null,
    val authors: List<BookAuthorWire>? = null,
    val series: MarginaliaSeriesWire? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("can_open") val canOpen: Boolean? = null,
    @SerialName("session_count") val sessionCount: Int? = null,
    @SerialName("active_session_count") val activeSessionCount: Int? = null,
    @SerialName("last_activity_at") val lastActivityAt: String? = null
)

@Serializable
internal data class MarginaliaSeriesWire(
    val id: String? = null,
    val name: String? = null,
    @SerialName("series_index") val seriesIndex: String? = null
)

@Serializable
internal data class ReadingSessionPageWire(
    val count: Int? = null,
    val next: String? = null,
    val previous: String? = null,
    val results: List<ReadingSessionWire>? = null
)

@Serializable
internal data class BookReadingSessionPageWire(
    val context: ReadingSessionContextWire? = null,
    val count: Int? = null,
    val next: String? = null,
    val previous: String? = null,
    val results: List<ReadingSessionWire>? = null
)

@Serializable
internal data class ReadingSessionDetailWire(
    val context: ReadingSessionContextWire? = null,
    val session: ReadingSessionWire? = null
)

@Serializable
internal data class ReadingSessionContextWire(val book: RecentReadingBookWire? = null)

@Serializable
internal data class ReadingSessionWire(
    val id: String? = null,
    val name: String? = null,
    val notes: String? = null,
    val status: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("closed_at") val closedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("last_activity_at") val lastActivityAt: String? = null,
    @SerialName("annotation_count") val annotationCount: Int? = null,
    val progress: ReadingProgressWire? = null,
    val book: RecentReadingBookWire? = null
)

@Serializable
internal data class ReadingSessionBootstrapWire(
    val created: Boolean? = null,
    val context: ReadingSessionContextWire? = null,
    val session: ReadingSessionWire? = null,
    val annotations: List<MarginaliaAnnotationWire>? = null,
    @SerialName("closed_sessions") val closedSessions: ReadingSessionSummaryPageWire? = null
)

@Serializable
internal data class ReadingSessionSummaryPageWire(
    val count: Int? = null,
    val next: String? = null,
    val previous: String? = null,
    val results: List<ReadingSessionWire>? = null
)

@Serializable
internal data class MarginaliaAnnotationWire(
    val id: String? = null,
    @SerialName("client_id") val clientId: String? = null,
    val kind: String? = null,
    val location: MarginaliaAnnotationLocationWire? = null,
    val body: MarginaliaHighlightBodyWire? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
internal data class MarginaliaAnnotationLocationWire(
    val cfi: String? = null,
    @SerialName("location_label") val locationLabel: String? = null
)

@Serializable
internal data class MarginaliaHighlightBodyWire(
    val text: String? = null,
    val prefix: String? = null,
    val suffix: String? = null,
    val color: String? = null,
    val note: String? = null
)

@Serializable
internal data class ReadingSessionMetadataInputWire(
    val name: String? = null,
    val notes: String? = null
)

@Serializable
internal data class ReadingProgressInputWire(
    val cfi: String,
    @SerialName("location_label") val locationLabel: String? = null
)

@Serializable
internal data class ReadingSessionFinalizationWire(
    val name: String? = null,
    val notes: String? = null,
    val progress: ReadingProgressInputWire? = null
)
