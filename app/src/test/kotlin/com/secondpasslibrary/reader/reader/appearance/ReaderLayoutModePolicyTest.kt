package com.secondpasslibrary.reader.reader.appearance

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderLayoutModePolicyTest {
    @Test
    fun `landscape preserves every saved layout preference`() {
        ReaderLayoutMode.entries.forEach { saved ->
            assertEquals(saved, saved.effectiveFor(ReaderViewportOrientation.LANDSCAPE))
        }
    }

    @Test
    fun `portrait forces single without changing saved preference`() {
        ReaderLayoutMode.entries.forEach { saved ->
            val effective = saved.effectiveFor(ReaderViewportOrientation.PORTRAIT)

            assertEquals(ReaderLayoutMode.SINGLE_COLUMN, effective)
        }
    }

    @Test
    fun `returning to landscape restores saved two column preference`() {
        val saved = ReaderLayoutMode.TWO_COLUMN

        assertEquals(
            ReaderLayoutMode.SINGLE_COLUMN,
            saved.effectiveFor(ReaderViewportOrientation.PORTRAIT)
        )
        assertEquals(saved, saved.effectiveFor(ReaderViewportOrientation.LANDSCAPE))
    }
}
