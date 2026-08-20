package com.secondpasslibrary.reader.marginalia

import com.secondpasslibrary.client.MarginaliaAnnotation
import com.secondpasslibrary.client.PublicBookCoverReference
import com.secondpasslibrary.client.ReadingSessionDetailResult
import com.secondpasslibrary.client.ReadingSessionStatus
import com.secondpasslibrary.reader.design.marginalia.AnnotationHighlightTone
import com.secondpasslibrary.reader.design.marginalia.toHighlightTone
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
    val closedNotice: String?
)

internal fun ReadingSessionDetailState.screenTitle(): String =
    detail?.book?.title ?: "Reading session"

internal fun ReadingSessionDetailPresentation.sessionNameOrFallback(): String =
    sessionName ?: "Unnamed reading session"

internal sealed interface ReadingSessionAnnotationPresentation {
    val label: String
    val locationLabel: String?
    val updatedLabel: String

    data class Bookmark(
        override val label: String = "Bookmark",
        override val locationLabel: String?,
        override val updatedLabel: String
    ) : ReadingSessionAnnotationPresentation

    data class Highlight(
        override val label: String,
        override val locationLabel: String?,
        override val updatedLabel: String,
        val quote: String,
        val note: String?,
        val tone: AnnotationHighlightTone
    ) : ReadingSessionAnnotationPresentation
}

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
        closedNotice = if (closed) "This reading session is closed." else null
    )
}

internal fun MarginaliaAnnotation.toPresentation(
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault()
): ReadingSessionAnnotationPresentation = when (this) {
    is MarginaliaAnnotation.Bookmark -> ReadingSessionAnnotationPresentation.Bookmark(
        locationLabel = location.locationLabel?.takeIf(String::isNotBlank),
        updatedLabel = formatSessionTimestamp(updatedAt, zoneId, locale)
    )

    is MarginaliaAnnotation.Highlight -> {
        val displayedNote = body.note?.takeIf(String::isNotBlank)
        ReadingSessionAnnotationPresentation.Highlight(
            label = if (displayedNote == null) "Highlight" else "Commented highlight",
            locationLabel = location.locationLabel?.takeIf(String::isNotBlank),
            updatedLabel = formatSessionTimestamp(updatedAt, zoneId, locale),
            quote = body.text,
            note = displayedNote,
            tone = body.color.toHighlightTone()
        )
    }
}
