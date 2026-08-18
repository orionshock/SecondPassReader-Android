package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.AuthorOrdering
import com.secondpasslibrary.client.BookOrdering
import com.secondpasslibrary.client.LibraryAuthor
import com.secondpasslibrary.client.LibraryPreviewBook
import com.secondpasslibrary.client.LibrarySeries
import com.secondpasslibrary.client.PublicBookCoverReference
import com.secondpasslibrary.client.SeriesOrdering
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryEntityPresentationTest {
    @Test
    fun `Author and Series cards map identity and book-count labels`() {
        val author = LibraryAuthor("a", "Author", "Author", "", 1, null)
        val series = LibrarySeries("s", "Series", "Series", "", 12, null)

        assertEquals("Author", author.toLibraryEntityPresentation().name)
        assertEquals("1 book", author.toLibraryEntityPresentation().bookCountLabel)
        assertEquals("Series", series.toLibraryEntityPresentation().name)
        assertEquals("12 books", series.toLibraryEntityPresentation().bookCountLabel)
    }

    @Test
    fun `preview omission empty return and populated return remain distinct`() {
        val omitted = authorWithPreviews(null).toLibraryEntityPresentation().previews
        val empty = authorWithPreviews(emptyList()).toLibraryEntityPresentation().previews
        val populated =
            authorWithPreviews(
                listOf(
                    LibraryPreviewBook("missing", "Missing", null),
                    LibraryPreviewBook(
                        "public",
                        "Public",
                        PublicBookCoverReference.fromAbsoluteUrl("https://covers.example/book.webp")
                    )
                )
            ).toLibraryEntityPresentation().previews

        assertEquals(LibraryPreviewBooksPresentation.Omitted, omitted)
        assertEquals(LibraryPreviewBooksPresentation.Returned(emptyList()), empty)
        val books = (populated as LibraryPreviewBooksPresentation.Returned).books
        assertEquals(LibraryBookCoverPresentation.Missing, books.first().cover)
        assertTrue(books.last().cover is LibraryBookCoverPresentation.Public)
    }

    @Test
    fun `presentation keeps no more than three preview covers`() {
        val previews = (
            authorWithPreviews(
                (1..4).map { LibraryPreviewBook("$it", "Book $it", null) }
            ).toLibraryEntityPresentation().previews as LibraryPreviewBooksPresentation.Returned
            ).books

        assertEquals(listOf("1", "2", "3"), previews.map { it.id })
    }

    @Test
    fun `axis sort controls expose the complete typed ordering vocabulary`() {
        assertEquals(
            listOf(
                AuthorOrdering.NAME,
                AuthorOrdering.NAME_DESCENDING,
                AuthorOrdering.BOOK_COUNT_DESCENDING,
                AuthorOrdering.BOOK_COUNT
            ),
            authorOrderingOptions().map { it.ordering }
        )
        assertEquals(
            listOf("Name A-Z", "Name Z-A", "Most books", "Fewest books"),
            seriesOrderingOptions().map { it.label }
        )
    }

    @Test
    fun `active axis maps committed search sort and count from its child`() {
        val state =
            LibraryState(
                axis = LibraryAxis.AUTHORS,
                resultKind = LibraryResultKind.AUTHOR_INDEX,
                authors =
                    LibraryEntityState(
                        committedQuery = "le guin",
                        ordering = AuthorOrdering.BOOK_COUNT_DESCENDING,
                        totalCount = 7,
                        currentPage = 1
                    )
            )

        assertEquals("le guin", state.committedQuery())
        assertEquals("Most books", state.orderingLabel())
        assertEquals(7, state.resultCount())
    }

    @Test
    fun `result kind owns Books layout-toggle availability`() {
        assertTrue(LibraryResultKind.BOOKS.supportsBookLayout)
        assertEquals(false, LibraryResultKind.AUTHOR_INDEX.supportsBookLayout)
        assertEquals(false, LibraryResultKind.SERIES_INDEX.supportsBookLayout)
    }

    @Test
    fun `filtered Books expose context-appropriate ordering choices`() {
        assertEquals(
            BookOrdering.TITLE,
            (
                libraryOrderingOptions(
                    LibraryBooksMode.BROWSE,
                    LibraryBooksFilter.Author("author")
                ).first().ordering as LibraryBooksOrdering.Browse
                ).value
        )
        assertEquals(
            BookOrdering.SERIES_INDEX,
            (
                libraryOrderingOptions(
                    LibraryBooksMode.BROWSE,
                    LibraryBooksFilter.Series("series")
                ).first().ordering as LibraryBooksOrdering.Browse
                ).value
        )
    }

    @Test
    fun `selected Author detail maps loading failure and content explicitly`() {
        val loading = LibraryEntityDetailState<LibraryAuthor>("a", loading = true)
        val failure =
            LibraryEntityDetailState<LibraryAuthor>(
                "a",
                failure = LibraryFailure.PROTOCOL_INVALID
            )
        val content =
            LibraryEntityDetailState(
                "a",
                detail = LibraryAuthor("a", "Author", "Author", "Biography", 2, emptyList())
            )

        assertTrue(
            loading.toAuthorDetailPresentation() is LibrarySelectedEntityPresentation.Loading
        )
        assertTrue(
            failure.toAuthorDetailPresentation() is LibrarySelectedEntityPresentation.Failure
        )
        val presented =
            content.toAuthorDetailPresentation() as LibrarySelectedEntityPresentation.Content
        assertEquals("Biography", presented.description)
        assertEquals("2 books", presented.bookCountLabel)
    }

    @Test
    fun `selected Series detail maps blank summary to absent description`() {
        val detail =
            LibraryEntityDetailState(
                "s",
                detail = LibrarySeries("s", "Series", "Series", "  ", 3, emptyList())
            )

        val presented =
            detail.toSeriesDetailPresentation() as LibrarySelectedEntityPresentation.Content
        assertEquals(null, presented.description)
        assertEquals("3 books", presented.bookCountLabel)
    }

    @Test
    fun `paging trigger remains based on proximity rather than axis type`() {
        assertTrue(shouldRequestNextPage(lastVisibleIndex = 14, itemCount = 20))
        assertEquals(false, shouldRequestNextPage(lastVisibleIndex = 4, itemCount = 20))
    }

    private fun authorWithPreviews(previews: List<LibraryPreviewBook>?) =
        LibraryAuthor("a", "Author", "Author", "", 2, previews)
}
