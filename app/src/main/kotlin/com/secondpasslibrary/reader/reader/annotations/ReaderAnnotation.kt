package com.secondpasslibrary.reader.reader.annotations

import com.secondpasslibrary.client.MarginaliaAnnotation
import com.secondpasslibrary.client.MarginaliaHighlightColor

internal sealed interface ReaderAnnotation {
    val id: String
    val cfi: String
    val locationLabel: String?
    val updatedAt: String

    data class Bookmark(
        override val id: String,
        override val cfi: String,
        override val locationLabel: String?,
        override val updatedAt: String
    ) : ReaderAnnotation

    data class Highlight(
        override val id: String,
        override val cfi: String,
        override val locationLabel: String?,
        override val updatedAt: String,
        val quote: String,
        val note: String?,
        val color: ReaderAnnotationColor
    ) : ReaderAnnotation
}

internal enum class ReaderAnnotationColor {
    YELLOW,
    GREEN,
    BLUE,
    PINK,
    PURPLE,
    ORANGE
}

internal fun MarginaliaAnnotation.toReaderAnnotation(): ReaderAnnotation = when (this) {
    is MarginaliaAnnotation.Bookmark -> ReaderAnnotation.Bookmark(
        id = id,
        cfi = location.cfi,
        locationLabel = location.locationLabel?.takeIf(String::isNotBlank),
        updatedAt = updatedAt
    )

    is MarginaliaAnnotation.Highlight -> ReaderAnnotation.Highlight(
        id = id,
        cfi = location.cfi,
        locationLabel = location.locationLabel?.takeIf(String::isNotBlank),
        updatedAt = updatedAt,
        quote = body.text,
        note = body.note?.takeIf(String::isNotBlank),
        color = body.color.toReaderAnnotationColor()
    )
}

private fun MarginaliaHighlightColor.toReaderAnnotationColor(): ReaderAnnotationColor =
    when (this) {
        MarginaliaHighlightColor.YELLOW -> ReaderAnnotationColor.YELLOW
        MarginaliaHighlightColor.GREEN -> ReaderAnnotationColor.GREEN
        MarginaliaHighlightColor.BLUE -> ReaderAnnotationColor.BLUE
        MarginaliaHighlightColor.PINK -> ReaderAnnotationColor.PINK
        MarginaliaHighlightColor.PURPLE -> ReaderAnnotationColor.PURPLE
        MarginaliaHighlightColor.ORANGE -> ReaderAnnotationColor.ORANGE
    }
