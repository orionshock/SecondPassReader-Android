package com.secondpasslibrary.reader.marginalia.detail

import com.secondpasslibrary.client.PublicBookCoverReference
import com.secondpasslibrary.client.ReadingSessionDetailResult
import com.secondpasslibrary.client.ReadingSessionStatus
import com.secondpasslibrary.reader.design.components.AppBarNavigation
import com.secondpasslibrary.reader.design.components.AppBarPresentation
import com.secondpasslibrary.reader.marginalia.annotationCountLabel
import com.secondpasslibrary.reader.marginalia.formatSessionTimestamp
import java.time.ZoneId
import java.util.Locale

internal data class ReadingSessionDetailPresentation(
    val bookTitle: String,
    val cover: PublicBookCoverReference?,
    val sessionName: String?,
    val notes: String?,
    val statusLabel: String,
    val active: Boolean,
    val annotationCountLabel: String,
    val progressLocation: String?,
    val startedLabel: String,
    val updatedLabel: String,
    val closedLabel: String?,
    val canOpenBook: Boolean
)

internal fun ReadingSessionDetailState.appBarPresentation(): AppBarPresentation {
    val result = detail
    return AppBarPresentation(
        navigation = AppBarNavigation.BACK,
        context = result?.book?.title,
        title = result?.session?.summary?.name?.takeIf(String::isNotBlank) ?: "Reading session"
    )
}

internal fun ReadingSessionDetailPresentation.sessionNameOrFallback(): String =
    sessionName ?: "Unnamed reading session"

internal fun ReadingSessionDetailResult.toDetailPresentation(
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault()
): ReadingSessionDetailPresentation {
    val summary = session.summary
    val closed = summary.status == ReadingSessionStatus.CLOSED
    return ReadingSessionDetailPresentation(
        bookTitle = book.title,
        cover = book.cover,
        sessionName = summary.name.takeIf(String::isNotBlank),
        notes = summary.notes.takeIf(String::isNotBlank),
        statusLabel = if (closed) "Closed" else "Active",
        active = !closed,
        annotationCountLabel = annotationCountLabel(summary.annotationCount),
        progressLocation = session.progress?.locationLabel?.takeIf(String::isNotBlank),
        startedLabel = formatSessionTimestamp(summary.startedAt, zoneId, locale),
        updatedLabel = formatSessionTimestamp(summary.updatedAt, zoneId, locale),
        closedLabel = summary.closedAt?.let { formatSessionTimestamp(it, zoneId, locale) },
        canOpenBook = book.canOpen
    )
}
