package com.secondpasslibrary.reader.reader.appearance

import kotlinx.coroutines.flow.StateFlow

internal enum class ReaderTheme {
    LIGHT,
    DARK,
    SEPIA
}

internal data class ReaderAppearance(
    val theme: ReaderTheme = ReaderTheme.SEPIA,
    val fontScale: Double = DEFAULT_FONT_SCALE,
    val lineHeight: Double = DEFAULT_LINE_HEIGHT,
    val publisherStylesEnabled: Boolean = false
) {
    init {
        require(fontScale in FONT_SCALE_RANGE) { "Font scale is out of range." }
        require(lineHeight in LINE_HEIGHT_RANGE) { "Line height is out of range." }
    }

    internal companion object {
        const val FONT_SCALE_STEP = 0.1
        const val LINE_HEIGHT_STEP = 0.1
        val FONT_SCALE_RANGE = 0.8..1.8
        val LINE_HEIGHT_RANGE = 1.0..2.0
        private const val DEFAULT_FONT_SCALE = 1.0
        private const val DEFAULT_LINE_HEIGHT = 1.4
    }
}

internal interface ReaderAppearanceController {
    val appearance: StateFlow<ReaderAppearance>

    suspend fun update(appearance: ReaderAppearance)
}
