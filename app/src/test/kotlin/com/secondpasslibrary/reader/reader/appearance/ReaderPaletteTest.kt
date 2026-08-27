package com.secondpasslibrary.reader.reader.appearance

import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPaletteTest {
    @Test
    fun `light and sepia are dark ink on light paper while dark is the inverse`() {
        val light = ReaderTheme.LIGHT.readerPalette()
        val sepia = ReaderTheme.SEPIA.readerPalette()
        val dark = ReaderTheme.DARK.readerPalette()

        assertTrue(light.publicationBackground.luminance() > 0.7f)
        assertTrue(light.publicationForeground.luminance() < 0.1f)
        assertTrue(sepia.publicationBackground.luminance() > 0.6f)
        assertTrue(sepia.publicationForeground.luminance() < 0.1f)
        assertTrue(sepia.primaryForeground.luminance() < sepia.publicationBackground.luminance())
        assertTrue(dark.publicationBackground.luminance() < 0.02f)
        assertTrue(dark.publicationForeground.luminance() > 0.7f)
    }

    @Test
    fun `Reader surfaces and controls derive from every theme palette`() {
        ReaderTheme.entries.forEach { theme ->
            val palette = theme.readerPalette()

            assertTrue(palette.primaryForeground != palette.panelSurface)
            assertTrue(palette.secondaryForeground != palette.floatingSurface)
            assertTrue(palette.border.alpha > 0f)
            assertTrue(palette.scrim.alpha > 0f)
        }
    }
}
