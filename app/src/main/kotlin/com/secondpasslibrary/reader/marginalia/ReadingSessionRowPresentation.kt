package com.secondpasslibrary.reader.marginalia

import com.secondpasslibrary.client.PublicBookCoverReference
import com.secondpasslibrary.client.ReadingSessionListItem
import com.secondpasslibrary.client.ReadingSessionStatus
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal data class ReadingSessionRowPresentation(
    val id: String,
    val bookTitle: String,
    val cover: PublicBookCoverReference?,
    val sessionName: String?,
    val statusLabel: String,
    val active: Boolean,
    val annotationCountLabel: String,
    val lastActivityLabel: String
)

internal fun ReadingSessionListItem.toRowPresentation(
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault()
) = ReadingSessionRowPresentation(
    id = session.id,
    bookTitle = book.title,
    cover = book.cover,
    sessionName = session.name.takeIf(String::isNotBlank),
    statusLabel = session.status.presentationLabel,
    active = session.status == ReadingSessionStatus.ACTIVE,
    annotationCountLabel = annotationCountLabel(session.annotationCount),
    lastActivityLabel = formatSessionTimestamp(session.lastActivityAt, zoneId, locale)
)

internal val ReadingSessionStatusFilter.presentationLabel: String
    get() = when (this) {
        ReadingSessionStatusFilter.ALL -> "All"
        ReadingSessionStatusFilter.ACTIVE -> "Active"
        ReadingSessionStatusFilter.CLOSED -> "Closed"
    }

internal fun ReadingSessionsState.screenTitle(): String = when (context) {
    MarginaliaHistoryContext.Global -> "Marginalia"

    is MarginaliaHistoryContext.Book ->
        book?.title?.let { "Reading sessions for $it" } ?: "Reading sessions"
}

internal fun ReadingSessionsState.emptyMessage(): String = when {
    context is MarginaliaHistoryContext.Book -> "No reading sessions for this book."
    statusFilter == ReadingSessionStatusFilter.ACTIVE -> "No active reading sessions."
    statusFilter == ReadingSessionStatusFilter.CLOSED -> "No closed reading sessions."
    else -> "No reading sessions yet."
}

internal fun shouldRequestMoreSessions(lastVisibleIndex: Int, itemCount: Int): Boolean =
    itemCount > 0 && lastVisibleIndex >= itemCount - SESSION_PAGING_THRESHOLD

internal fun sessionCountLabel(count: Int): String =
    if (count == 1) "1 session" else "$count sessions"

internal fun formatSessionTimestamp(value: String, zoneId: ZoneId, locale: Locale): String {
    val instant = runCatching { Instant.parse(value) }
        .recoverCatching { OffsetDateTime.parse(value).toInstant() }
        .getOrNull() ?: return value
    return DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(zoneId)
        .format(instant)
}

private val ReadingSessionStatus.presentationLabel: String
    get() = when (this) {
        ReadingSessionStatus.ACTIVE -> "Active"
        ReadingSessionStatus.CLOSED -> "Closed"
    }

private fun annotationCountLabel(count: Int) =
    if (count == 1) "1 annotation" else "$count annotations"

private const val SESSION_PAGING_THRESHOLD = 5
