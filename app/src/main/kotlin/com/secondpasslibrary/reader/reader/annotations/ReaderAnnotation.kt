package com.secondpasslibrary.reader.reader.annotations

import com.secondpasslibrary.client.MarginaliaAnnotation
import com.secondpasslibrary.client.MarginaliaHighlightColor

internal sealed interface ReaderAnnotation {
    val id: String
    val clientId: String
    val cfi: String
    val locationLabel: String?
    val updatedAt: String

    data class Bookmark(
        override val id: String,
        override val clientId: String,
        override val cfi: String,
        override val locationLabel: String?,
        override val updatedAt: String
    ) : ReaderAnnotation

    data class Highlight(
        override val id: String,
        override val clientId: String,
        override val cfi: String,
        override val locationLabel: String?,
        override val updatedAt: String,
        val quote: String,
        val prefix: String?,
        val suffix: String?,
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
    ORANGE;

    /** Exact Second Pass web Reader palette, kept separate from the server token. */
    val displayArgb: Long
        get() = when (this) {
            YELLOW -> 0xFFFACC15
            GREEN -> 0xFF22C55E
            BLUE -> 0xFF3B82F6
            PINK -> 0xFFEC4899
            PURPLE -> 0xFFA855F7
            ORANGE -> 0xFFF97316
        }
}

internal fun MarginaliaAnnotation.toReaderAnnotation(): ReaderAnnotation = when (this) {
    is MarginaliaAnnotation.Bookmark -> ReaderAnnotation.Bookmark(
        id = id,
        clientId = clientId,
        cfi = location.location,
        locationLabel = location.locationLabel?.takeIf(String::isNotBlank),
        updatedAt = updatedAt
    )

    is MarginaliaAnnotation.Highlight -> ReaderAnnotation.Highlight(
        id = id,
        clientId = clientId,
        cfi = location.location,
        locationLabel = location.locationLabel?.takeIf(String::isNotBlank),
        updatedAt = updatedAt,
        quote = body.text,
        prefix = body.prefix,
        suffix = body.suffix,
        note = body.note,
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
