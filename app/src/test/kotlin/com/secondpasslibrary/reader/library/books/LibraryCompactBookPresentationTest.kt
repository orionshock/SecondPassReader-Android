package com.secondpasslibrary.reader.library.books

import com.secondpasslibrary.client.BookAuthorSummary
import com.secondpasslibrary.client.BookSeriesSummary
import com.secondpasslibrary.client.CompactBook
import com.secondpasslibrary.client.PublicBookCoverReference
import com.secondpasslibrary.client.PublicationDatePrecision
import com.secondpasslibrary.client.SeriesIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryCompactBookPresentationTest {
    @Test
    fun `Library presentation keeps ordered authors and exact series index`() {
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
            ).toLibraryCompactBookPresentation()

        assertEquals("Second Author, First Author", presentation.authors)
        assertEquals("Sequence · 12.50", presentation.series)
    }

    @Test
    fun `missing and public covers map without inventing URLs`() {
        assertNull(book().toLibraryCompactBookPresentation().cover)
        val reference = PublicBookCoverReference.fromAbsoluteUrl("https://covers.example/book.webp")

        assertEquals(reference, book(cover = reference).toLibraryCompactBookPresentation().cover)
    }

    @Test
    fun `blank optional display metadata is omitted`() {
        val presentation =
            book(subtitle = "", publisher = "").toLibraryCompactBookPresentation()

        assertNull(presentation.subtitle)
        assertNull(presentation.authors)
        assertNull(presentation.publisher)
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
