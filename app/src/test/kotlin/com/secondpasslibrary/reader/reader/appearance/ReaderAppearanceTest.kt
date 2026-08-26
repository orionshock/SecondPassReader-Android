package com.secondpasslibrary.reader.reader.appearance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReaderAppearanceTest {
    @Test
    fun `default appearance is valid and uses the Sepia Reader baseline`() {
        val appearance = ReaderAppearance()

        assertEquals(ReaderTheme.SEPIA, appearance.theme)
        assertEquals(1.0, appearance.fontScale, 0.0)
        assertEquals(1.4, appearance.lineHeight, 0.0)
        assertEquals(false, appearance.publisherStylesEnabled)
    }

    @Test
    fun `font scale and line height reject values outside their UI bounds`() {
        assertThrows(IllegalArgumentException::class.java) {
            ReaderAppearance(fontScale = ReaderAppearance.FONT_SCALE_RANGE.start - 0.1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReaderAppearance(lineHeight = ReaderAppearance.LINE_HEIGHT_RANGE.endInclusive + 0.1)
        }
    }
}
