package com.secondpasslibrary.reader.marginalia.history

import androidx.compose.ui.unit.dp
import com.secondpasslibrary.client.PublicBookCoverReference
import com.secondpasslibrary.client.ReadingSessionListItem
import com.secondpasslibrary.client.ReadingSessionStatus
import com.secondpasslibrary.reader.design.components.AppBarNavigation
import com.secondpasslibrary.reader.design.components.AppBarPresentation
import com.secondpasslibrary.reader.design.marginalia.annotationCountLabel
import com.secondpasslibrary.reader.design.marginalia.formatMarginaliaTimestamp
import com.secondpasslibrary.reader.marginalia.MarginaliaHistoryContext
import java.time.ZoneId
import java.util.Locale

internal data class ReadingSessionRowPresentation(
    val id: String,
    val bookTitle: String,
    val cover: PublicBookCoverReference?,
    val sessionName: String?,
    val statusLabel: String,
    val active: Boolean,
    val annotationCountLabel: String,
    val lastActivityLabel: String,
    val noteExcerpt: String?
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
    lastActivityLabel = formatMarginaliaTimestamp(session.lastActivityAt, zoneId, locale),
    noteExcerpt = session.notes.trim().takeIf(String::isNotEmpty)
)

internal val ReadingSessionStatusFilter.presentationLabel: String
    get() = when (this) {
        ReadingSessionStatusFilter.ALL -> "All"
        ReadingSessionStatusFilter.ACTIVE -> "Active"
        ReadingSessionStatusFilter.CLOSED -> "Closed"
    }

internal fun ReadingSessionsState.appBarPresentation(): AppBarPresentation = when (context) {
    MarginaliaHistoryContext.Global ->
        AppBarPresentation(
            AppBarNavigation.MENU,
            title = "Marginalia",
            metadata = loadedSessionCountLabel(),
            metadataSlotWidth = MARGINALIA_COUNT_SLOT_WIDTH
        )

    is MarginaliaHistoryContext.Book ->
        AppBarPresentation(
            AppBarNavigation.BACK,
            context = book?.title,
            title = "Reading sessions",
            metadata = loadedSessionCountLabel()
        )
}

private fun ReadingSessionsState.loadedSessionCountLabel(): String? =
    sessionCountLabel(totalCount).takeIf { currentPage > 0 }

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

private val ReadingSessionStatus.presentationLabel: String
    get() = when (this) {
        ReadingSessionStatus.ACTIVE -> "Active"
        ReadingSessionStatus.CLOSED -> "Closed"
    }

private const val SESSION_PAGING_THRESHOLD = 5
internal const val SESSION_NOTE_MAX_LINES = 3
private val MARGINALIA_COUNT_SLOT_WIDTH = 84.dp
