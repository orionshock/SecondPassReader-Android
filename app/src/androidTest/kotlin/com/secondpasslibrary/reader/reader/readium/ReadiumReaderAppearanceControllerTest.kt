package com.secondpasslibrary.reader.reader.readium

import androidx.compose.ui.graphics.toArgb
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearance
import com.secondpasslibrary.reader.reader.appearance.ReaderTheme
import com.secondpasslibrary.reader.reader.appearance.readerPalette
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.preferences.Theme

@RunWith(AndroidJUnit4::class)
class ReadiumReaderAppearanceControllerTest {
    @Test
    fun appAppearanceMapsToReadiumWhilePaginatedModeRemainsFixed() {
        ReaderTheme.entries.forEach { readerTheme ->
            val appearance = ReaderAppearance(
                theme = readerTheme,
                fontScale = 1.3,
                lineHeight = 1.7,
                publisherStylesEnabled = true
            )

            val preferences = appearance.toReadiumPreferences()

            assertEquals(readerTheme.expectedReadiumTheme(), preferences.theme)
            assertEquals(
                readerTheme.readerPalette().publicationBackground.toArgb(),
                preferences.backgroundColor?.int
            )
            assertEquals(
                readerTheme.readerPalette().publicationForeground.toArgb(),
                preferences.textColor?.int
            )
            assertEquals(1.3, preferences.fontSize)
            assertEquals(1.7, preferences.lineHeight)
            assertEquals(true, preferences.publisherStyles)
            assertFalse(preferences.scroll ?: true)
        }
    }

    @Test
    fun unboundUpdateIsRetainedForNavigatorRecreation() = runTest {
        val controller = ReadiumReaderAppearanceController()
        val updated = ReaderAppearance(theme = ReaderTheme.SEPIA, fontScale = 1.2)

        controller.update(updated)

        assertEquals(updated, controller.appearance.value)
        assertEquals(Theme.SEPIA, controller.initialPreferences().theme)
        assertEquals(1.2, controller.initialPreferences().fontSize)
    }
}

private fun ReaderTheme.expectedReadiumTheme(): Theme = when (this) {
    ReaderTheme.LIGHT -> Theme.LIGHT
    ReaderTheme.DARK -> Theme.DARK
    ReaderTheme.SEPIA -> Theme.SEPIA
}
