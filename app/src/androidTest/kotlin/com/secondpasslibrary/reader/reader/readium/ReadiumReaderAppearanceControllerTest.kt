package com.secondpasslibrary.reader.reader.readium

import androidx.compose.ui.graphics.toArgb
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearance
import com.secondpasslibrary.reader.reader.appearance.ReaderLayoutMode
import com.secondpasslibrary.reader.reader.appearance.ReaderTheme
import com.secondpasslibrary.reader.reader.appearance.ReaderViewportOrientation
import com.secondpasslibrary.reader.reader.appearance.readerPalette
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.Spread
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalReadiumApi::class)
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
            assertEquals(1.0, preferences.pageMargins)
            assertFalse(preferences.scroll ?: true)
        }
    }

    @Test
    fun layoutModesMapToExplicitColumnAndSpreadPreferences() {
        val expected = mapOf(
            ReaderLayoutMode.SINGLE_COLUMN to (ColumnCount.ONE to Spread.NEVER),
            ReaderLayoutMode.AUTO to (ColumnCount.AUTO to null),
            ReaderLayoutMode.TWO_COLUMN to (ColumnCount.TWO to Spread.ALWAYS)
        )

        expected.forEach { (mode, readium) ->
            val preferences = ReaderAppearance(layoutMode = mode).toReadiumPreferences()

            assertEquals(readium.first, preferences.columnCount)
            assertEquals(readium.second, preferences.spread)
            assertFalse(preferences.scroll ?: true)
        }
    }

    @Test
    fun unboundUpdateIsRetainedForNavigatorRecreation() = runTest {
        val controller = ReadiumReaderAppearanceController()
        val updated = ReaderAppearance(
            theme = ReaderTheme.SEPIA,
            fontScale = 1.2,
            layoutMode = ReaderLayoutMode.TWO_COLUMN
        )

        controller.update(updated)

        assertEquals(updated, controller.appearance.value)
        assertEquals(Theme.SEPIA, controller.initialPreferences().theme)
        assertEquals(1.2, controller.initialPreferences().fontSize)
        assertEquals(ColumnCount.TWO, controller.initialPreferences().columnCount)
        assertEquals(Spread.ALWAYS, controller.initialPreferences().spread)
    }

    @Test
    fun portraitOverrideDoesNotMutateSavedTwoColumnPreference() = runTest {
        val saved = ReaderAppearance(layoutMode = ReaderLayoutMode.TWO_COLUMN)
        val controller = ReadiumReaderAppearanceController(
            saved,
            ReaderViewportOrientation.LANDSCAPE
        )

        controller.updateViewportOrientation(ReaderViewportOrientation.PORTRAIT)

        assertEquals(saved, controller.appearance.value)
        assertEquals(ColumnCount.ONE, controller.initialPreferences().columnCount)
        assertEquals(Spread.NEVER, controller.initialPreferences().spread)

        controller.updateViewportOrientation(ReaderViewportOrientation.LANDSCAPE)

        assertEquals(saved, controller.appearance.value)
        assertEquals(ColumnCount.TWO, controller.initialPreferences().columnCount)
        assertEquals(Spread.ALWAYS, controller.initialPreferences().spread)
    }

    @Test
    fun readerCssUsesWiderLineLengthAndComfortableGutter() {
        val properties = readerCssProperties()

        assertEquals("68rem", properties.toCssProperties()["--RS__maxLineLength"])
        assertEquals("32px", properties.toCssProperties()["--RS__pageGutter"])
    }
}

private fun ReaderTheme.expectedReadiumTheme(): Theme = when (this) {
    ReaderTheme.LIGHT -> Theme.LIGHT
    ReaderTheme.DARK -> Theme.DARK
    ReaderTheme.SEPIA -> Theme.SEPIA
}
