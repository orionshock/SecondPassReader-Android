package com.secondpasslibrary.reader.marginalia.detail.annotations

import com.secondpasslibrary.client.MarginaliaAnnotation
import com.secondpasslibrary.reader.design.marginalia.AnnotationHighlightTone
import com.secondpasslibrary.reader.design.marginalia.formatMarginaliaTimestamp
import com.secondpasslibrary.reader.design.marginalia.toHighlightTone
import java.time.ZoneId
import java.util.Locale

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

internal fun MarginaliaAnnotation.toPresentation(
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault()
): ReadingSessionAnnotationPresentation = when (this) {
    is MarginaliaAnnotation.Bookmark -> ReadingSessionAnnotationPresentation.Bookmark(
        locationLabel = location.locationLabel?.takeIf(String::isNotBlank),
        updatedLabel = formatMarginaliaTimestamp(updatedAt, zoneId, locale)
    )

    is MarginaliaAnnotation.Highlight -> {
        val displayedNote = body.note?.takeIf(String::isNotBlank)
        ReadingSessionAnnotationPresentation.Highlight(
            label = if (displayedNote == null) "Highlight" else "Commented highlight",
            locationLabel = location.locationLabel?.takeIf(String::isNotBlank),
            updatedLabel = formatMarginaliaTimestamp(updatedAt, zoneId, locale),
            quote = body.text,
            note = displayedNote,
            tone = body.color.toHighlightTone()
        )
    }
}
