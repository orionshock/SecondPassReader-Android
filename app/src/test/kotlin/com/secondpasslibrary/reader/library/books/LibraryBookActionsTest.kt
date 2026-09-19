package com.secondpasslibrary.reader.library.books

import com.secondpasslibrary.client.BookAuthorSummary
import com.secondpasslibrary.client.BookSeriesSummary
import com.secondpasslibrary.client.CompactBook
import com.secondpasslibrary.client.PublicationDatePrecision
import com.secondpasslibrary.reader.design.book.BookCardAction
import com.secondpasslibrary.reader.design.book.menuLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LibraryBookActionsTest {
    @Test
    fun `single author and series produce contextual navigation actions`() {
        val actions = book().bookCardActions()

        assertEquals(
            listOf(
                BookCardAction.MakeAvailableOffline("book-1"),
                BookCardAction.BookDetails("book-1"),
                BookCardAction.ReadingSessions("book-1"),
                BookCardAction.Author("book-1", "author-1", "Jim Butcher"),
                BookCardAction.Series("book-1", "series-1", "Dresden Files")
            ),
            actions
        )
        assertFalse(actions.map(BookCardAction::menuLabel).contains("Read book"))
    }

    @Test
    fun `ambiguous author and missing series omit contextual actions`() {
        val actions =
            book(
                authors =
                    listOf(
                        BookAuthorSummary("author-1", "One"),
                        BookAuthorSummary("author-2", "Two")
                    ),
                series = null
            ).bookCardActions()

        assertEquals(3, actions.size)
        assertFalse(actions.any { it is BookCardAction.Author })
        assertFalse(actions.any { it is BookCardAction.Series })
    }

    @Test
    fun `downloaded Book offers removal and offline mode keeps only local action`() {
        assertEquals(
            BookCardAction.RemoveDownload("book-1"),
            book().bookCardActions(downloaded = true).first()
        )
        assertEquals(
            listOf(BookCardAction.RemoveDownload("book-1")),
            book().bookCardActions(downloaded = true, offline = true)
        )
        val busyActions = book().bookCardActions(busy = true, offline = true)
        assertEquals(emptyList<BookCardAction>(), busyActions)
    }

    private fun book(
        authors: List<BookAuthorSummary> = listOf(BookAuthorSummary("author-1", "Jim Butcher")),
        series: BookSeriesSummary? = BookSeriesSummary("series-1", "Dresden Files", "", null)
    ) = CompactBook(
        id = "book-1",
        title = "Storm Front",
        sortTitle = "Storm Front",
        subtitle = "",
        authors = authors,
        series = series,
        catalogTags = emptyList(),
        language = null,
        publisher = null,
        publishedYear = null,
        publishedMonth = null,
        publishedDay = null,
        publicationDatePrecision = PublicationDatePrecision.UNSPECIFIED,
        cover = null,
        fileFormat = "EPUB"
    )
}
