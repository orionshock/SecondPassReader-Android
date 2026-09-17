package com.secondpasslibrary.reader.library.chrome

import com.secondpasslibrary.client.LibraryAuthor
import com.secondpasslibrary.client.LibrarySeries
import com.secondpasslibrary.reader.library.LibraryAxis
import com.secondpasslibrary.reader.library.LibraryResultState
import com.secondpasslibrary.reader.library.LibraryState
import com.secondpasslibrary.reader.library.axis.PagedLibraryAxisDetailState
import com.secondpasslibrary.reader.library.books.LibraryBooksState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibrarySelectorPresentationTest {
    @Test
    fun `return context belongs only to a concretely selected Author or Series`() {
        assertNull(authorIndex().selectedEntityAxis())
        assertEquals(LibraryAxis.AUTHORS, selectedAuthor().selectedEntityAxis())
        assertNull(seriesIndex().selectedEntityAxis())
        assertEquals(LibraryAxis.SERIES, selectedSeries().selectedEntityAxis())
        assertNull(LibraryState().selectedEntityAxis())
    }

    private fun authorIndex() = LibraryState(
        result = LibraryResultState.AuthorIndex()
    )

    private fun selectedAuthor() = LibraryState(
        result =
            LibraryResultState.AuthorBooks(
                PagedLibraryAxisDetailState(
                    "author-1",
                    LibraryAuthor("author-1", "Author", "Author", "", 1, null)
                ),
                books = LibraryBooksState()
            )
    )

    private fun seriesIndex() = LibraryState(
        result = LibraryResultState.SeriesIndex()
    )

    private fun selectedSeries() = LibraryState(
        result =
            LibraryResultState.SeriesBooks(
                PagedLibraryAxisDetailState(
                    "series-1",
                    LibrarySeries("series-1", "Series", "Series", "", 1, null)
                ),
                books = LibraryBooksState()
            )
    )
}
