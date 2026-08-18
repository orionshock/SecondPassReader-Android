package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.BookAuthorSummary
import com.secondpasslibrary.client.BookOrdering
import com.secondpasslibrary.client.BookSeriesSummary
import com.secondpasslibrary.client.CompactBook
import com.secondpasslibrary.client.LibrarySearchOrdering
import com.secondpasslibrary.client.PublicBookCoverReference
import com.secondpasslibrary.client.PublicationDatePrecision
import com.secondpasslibrary.client.SeriesIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryBookPresentationTest {
    @Test
    fun `book presentation keeps ordered authors and exact series index`() {
        val presentation =
            book(
                authors =
                    listOf(
                        BookAuthorSummary("2", "Second Author"),
                        BookAuthorSummary("1", "First Author")
                    ),
                series =
                    BookSeriesSummary(
                        "series",
                        "Sequence",
                        "Sequence",
                        SeriesIndex.fromExactValue("12.50")
                    )
            ).toLibraryPresentation()

        assertEquals("Second Author, First Author", presentation.authors)
        assertEquals("Sequence · 12.50", presentation.series)
    }

    @Test
    fun `missing and public covers map without inventing URLs`() {
        assertEquals(
            LibraryBookCoverPresentation.Missing,
            book().toLibraryPresentation().cover
        )
        val reference = PublicBookCoverReference.fromAbsoluteUrl("https://covers.example/book.webp")

        val cover = book(cover = reference).toLibraryPresentation().cover

        assertEquals(LibraryBookCoverPresentation.Public(reference), cover)
    }

    @Test
    fun `blank optional display metadata is omitted`() {
        val presentation = book(subtitle = "", publisher = "").toLibraryPresentation()

        assertNull(presentation.subtitle)
        assertNull(presentation.authors)
        assertNull(presentation.publisher)
    }

    @Test
    fun `ordering choices remain bounded by Library mode`() {
        val browse = libraryOrderingOptions(LibraryBooksMode.BROWSE).map { it.ordering }
        val broad = libraryOrderingOptions(LibraryBooksMode.BROAD_SEARCH).map { it.ordering }

        assertTrue(browse.contains(LibraryBooksOrdering.Browse(BookOrdering.SERIES_INDEX)))
        assertTrue(browse.contains(LibraryBooksOrdering.Browse(BookOrdering.PUBLISHER)))
        assertFalse(
            broad.any { it is LibraryBooksOrdering.Browse }
        )
        assertTrue(
            broad.contains(
                LibraryBooksOrdering.BroadSearch(LibrarySearchOrdering.SERIES_DESCENDING)
            )
        )
    }

    @Test
    fun `paging trigger starts only near the loaded result boundary`() {
        assertFalse(shouldRequestNextPage(lastVisibleIndex = 10, itemCount = 50))
        assertTrue(shouldRequestNextPage(lastVisibleIndex = 44, itemCount = 50))
        assertFalse(shouldRequestNextPage(lastVisibleIndex = -1, itemCount = 0))
    }

    private fun book(
        subtitle: String = "",
        authors: List<BookAuthorSummary> = emptyList(),
        series: BookSeriesSummary? = null,
        publisher: String? = null,
        cover: PublicBookCoverReference? = null
    ) = CompactBook(
        id = "book",
        title = "Book",
        sortTitle = "Book",
        subtitle = subtitle,
        authors = authors,
        series = series,
        catalogTags = emptyList(),
        language = "en",
        publisher = publisher,
        publishedYear = null,
        publishedMonth = null,
        publishedDay = null,
        publicationDatePrecision = PublicationDatePrecision.UNSPECIFIED,
        cover = cover,
        fileFormat = "epub"
    )
}
