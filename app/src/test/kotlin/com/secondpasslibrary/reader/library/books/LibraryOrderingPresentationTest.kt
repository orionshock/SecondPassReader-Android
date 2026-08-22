package com.secondpasslibrary.reader.library.books

import com.secondpasslibrary.client.BookOrdering
import com.secondpasslibrary.client.LibrarySearchOrdering
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryOrderingPresentationTest {
    @Test
    fun `ordering choices remain bounded by Library mode`() {
        val browse = libraryOrderingOptions(LibraryBooksMode.BROWSE).map { it.ordering }
        val broad = libraryOrderingOptions(LibraryBooksMode.BROAD_SEARCH).map { it.ordering }

        assertTrue(browse.contains(LibraryBooksOrdering.Browse(BookOrdering.SERIES_INDEX)))
        assertTrue(browse.contains(LibraryBooksOrdering.Browse(BookOrdering.PUBLISHER)))
        assertFalse(broad.any { it is LibraryBooksOrdering.Browse })
        assertTrue(
            broad.contains(
                LibraryBooksOrdering.BroadSearch(LibrarySearchOrdering.SERIES_DESCENDING)
            )
        )
    }

    @Test
    fun `filtered Books expose context-appropriate ordering choices`() {
        assertTrue(
            libraryOrderingOptions(
                LibraryBooksMode.BROWSE,
                LibraryBooksFilter.Author("author")
            ).first().ordering == LibraryBooksOrdering.Browse(BookOrdering.TITLE)
        )
        assertTrue(
            libraryOrderingOptions(
                LibraryBooksMode.BROWSE,
                LibraryBooksFilter.Series("series")
            ).first().ordering == LibraryBooksOrdering.Browse(BookOrdering.SERIES_INDEX)
        )
    }
}
