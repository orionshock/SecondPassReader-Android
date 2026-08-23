package com.secondpasslibrary.reader.marginalia

import org.junit.Assert.assertEquals
import org.junit.Test

class MarginaliaPresentationTest {
    @Test
    fun `root mode control remains the rightmost control across browse modes`() {
        assertEquals(
            listOf(MarginaliaChromeControl.STATUS, MarginaliaChromeControl.BROWSE_MODE),
            marginaliaChromeControlOrder(MarginaliaBrowseMode.SESSIONS, bookScoped = false)
        )
        assertEquals(
            listOf(MarginaliaChromeControl.BROWSE_MODE),
            marginaliaChromeControlOrder(MarginaliaBrowseMode.BOOKS, bookScoped = false)
        )
    }

    @Test
    fun `Book scoped history keeps status without root mode control`() {
        assertEquals(
            listOf(MarginaliaChromeControl.STATUS),
            marginaliaChromeControlOrder(MarginaliaBrowseMode.SESSIONS, bookScoped = true)
        )
    }
}
