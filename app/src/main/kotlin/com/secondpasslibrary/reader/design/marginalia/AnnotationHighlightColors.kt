package com.secondpasslibrary.reader.design.marginalia

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.secondpasslibrary.client.MarginaliaHighlightColor

internal enum class AnnotationHighlightTone {
    YELLOW,
    GREEN,
    BLUE,
    PINK,
    PURPLE,
    ORANGE
}

internal fun MarginaliaHighlightColor.toHighlightTone(): AnnotationHighlightTone = when (this) {
    MarginaliaHighlightColor.YELLOW -> AnnotationHighlightTone.YELLOW
    MarginaliaHighlightColor.GREEN -> AnnotationHighlightTone.GREEN
    MarginaliaHighlightColor.BLUE -> AnnotationHighlightTone.BLUE
    MarginaliaHighlightColor.PINK -> AnnotationHighlightTone.PINK
    MarginaliaHighlightColor.PURPLE -> AnnotationHighlightTone.PURPLE
    MarginaliaHighlightColor.ORANGE -> AnnotationHighlightTone.ORANGE
}

@Immutable
internal data class AnnotationHighlightPalette(val accent: Color, val background: Color)

@Composable
internal fun annotationHighlightPalette(tone: AnnotationHighlightTone): AnnotationHighlightPalette {
    val accent = when (tone) {
        AnnotationHighlightTone.YELLOW -> Color(0xFFFACC15)
        AnnotationHighlightTone.GREEN -> Color(0xFF22C55E)
        AnnotationHighlightTone.BLUE -> Color(0xFF3B82F6)
        AnnotationHighlightTone.PINK -> Color(0xFFEC4899)
        AnnotationHighlightTone.PURPLE -> Color(0xFFA855F7)
        AnnotationHighlightTone.ORANGE -> Color(0xFFF97316)
    }
    return AnnotationHighlightPalette(accent = accent, background = accent.copy(alpha = 0.10f))
}
