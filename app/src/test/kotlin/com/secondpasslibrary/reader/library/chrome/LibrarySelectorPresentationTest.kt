package com.secondpasslibrary.reader.library.chrome

import com.secondpasslibrary.client.AuthorOrdering
import com.secondpasslibrary.client.LibraryAuthor
import com.secondpasslibrary.client.LibrarySeries
import com.secondpasslibrary.client.SeriesOrdering
import com.secondpasslibrary.reader.library.LibraryAxis
import com.secondpasslibrary.reader.library.LibraryResultKind
import com.secondpasslibrary.reader.library.LibraryState
import com.secondpasslibrary.reader.library.axis.PagedLibraryAxisDetailState
import com.secondpasslibrary.reader.library.axis.PagedLibraryAxisState
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
        axis = LibraryAxis.AUTHORS,
        resultKind = LibraryResultKind.AUTHOR_INDEX,
        authors = PagedLibraryAxisState(ordering = AuthorOrdering.NAME)
    )

    private fun selectedAuthor() = authorIndex().copy(
        resultKind = LibraryResultKind.BOOKS,
        authors =
            PagedLibraryAxisState(
                ordering = AuthorOrdering.NAME,
                selected =
                    PagedLibraryAxisDetailState(
                        "author-1",
                        LibraryAuthor("author-1", "Author", "Author", "", 1, null)
                    )
            )
    )

    private fun seriesIndex() = LibraryState(
        axis = LibraryAxis.SERIES,
        resultKind = LibraryResultKind.SERIES_INDEX,
        series = PagedLibraryAxisState(ordering = SeriesOrdering.NAME)
    )

    private fun selectedSeries() = seriesIndex().copy(
        resultKind = LibraryResultKind.BOOKS,
        series =
            PagedLibraryAxisState(
                ordering = SeriesOrdering.NAME,
                selected =
                    PagedLibraryAxisDetailState(
                        "series-1",
                        LibrarySeries("series-1", "Series", "Series", "", 1, null)
                    )
            )
    )
}
