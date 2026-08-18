package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.LibraryCatalogTag
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogTagFilterPresentationTest {
    @Test
    fun `book count label uses singular and plural forms`() {
        assertEquals("1 book", tag(1).bookCountLabel())
        assertEquals("0 books", tag(0).bookCountLabel())
        assertEquals("12 books", tag(12).bookCountLabel())
    }

    private fun tag(count: Int) = LibraryCatalogTag("id", "Fiction", "fiction", count)
}
