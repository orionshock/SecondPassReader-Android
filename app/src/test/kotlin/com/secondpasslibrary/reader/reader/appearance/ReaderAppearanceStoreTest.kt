package com.secondpasslibrary.reader.reader.appearance

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderAppearanceStoreTest {
    @Test
    fun `fresh and missing persisted values use canonical Sepia defaults`() {
        val restored = PersistedReaderAppearance(null, null, null, null).toReaderAppearance()

        assertEquals(ReaderAppearance(), restored)
        assertEquals(ReaderTheme.SEPIA, restored.theme)
        assertEquals(ReaderLayoutMode.SINGLE_COLUMN, restored.layoutMode)
    }

    @Test
    fun `app-owned appearance representation round trips`() {
        val appearance = ReaderAppearance(
            theme = ReaderTheme.LIGHT,
            fontScale = 1.3,
            lineHeight = 1.8,
            publisherStylesEnabled = true,
            layoutMode = ReaderLayoutMode.TWO_COLUMN
        )

        assertEquals(appearance, appearance.toPersistedReaderAppearance().toReaderAppearance())
    }

    @Test
    fun `explicit Sepia round trips`() {
        val appearance = ReaderAppearance(theme = ReaderTheme.SEPIA)

        assertEquals(appearance, appearance.toPersistedReaderAppearance().toReaderAppearance())
    }

    @Test
    fun `every explicit layout mode round trips`() {
        ReaderLayoutMode.entries.forEach { mode ->
            val appearance = ReaderAppearance(layoutMode = mode)

            assertEquals(appearance, appearance.toPersistedReaderAppearance().toReaderAppearance())
        }
    }

    @Test
    fun `unknown malformed and out-of-range values recover safely`() {
        val restored = PersistedReaderAppearance(
            theme = "MIDNIGHT",
            fontScale = "99.0",
            lineHeight = "not-a-number",
            publisherStylesEnabled = null,
            layoutMode = "FOUR_COLUMN"
        ).toReaderAppearance()

        assertEquals(ReaderTheme.SEPIA, restored.theme)
        assertEquals(ReaderAppearance.FONT_SCALE_RANGE.endInclusive, restored.fontScale, 0.0)
        assertEquals(ReaderAppearance().lineHeight, restored.lineHeight, 0.0)
        assertEquals(ReaderAppearance().publisherStylesEnabled, restored.publisherStylesEnabled)
        assertEquals(ReaderLayoutMode.SINGLE_COLUMN, restored.layoutMode)
    }
}
