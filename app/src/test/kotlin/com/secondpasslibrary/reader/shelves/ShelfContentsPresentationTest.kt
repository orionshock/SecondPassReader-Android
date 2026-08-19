package com.secondpasslibrary.reader.shelves

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfContentsPresentationTest {
    @Test
    fun `stored position is presented one based`() {
        assertEquals(1, userFacingShelfPosition(0))
        assertEquals(10, userFacingShelfPosition(9))
    }

    @Test
    fun `paging trigger uses loaded entries without defining move boundaries`() {
        assertFalse(shouldRequestEditorNextPage(2, 10))
        assertTrue(shouldRequestEditorNextPage(5, 10))
    }

    @Test
    fun `counts retain visible and unavailable distinction`() {
        assertEquals("7 visible / 2 unavailable", shelfEditorCountsLabel(7, 2))
    }
}
