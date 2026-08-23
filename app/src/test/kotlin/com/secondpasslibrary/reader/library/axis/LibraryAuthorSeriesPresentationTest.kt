package com.secondpasslibrary.reader.library.axis

import com.secondpasslibrary.client.AuthorOrdering
import com.secondpasslibrary.client.LibraryAuthor
import com.secondpasslibrary.client.LibraryPreviewBook
import com.secondpasslibrary.client.LibrarySeries
import com.secondpasslibrary.client.PublicBookCoverReference
import com.secondpasslibrary.client.SeriesOrdering
import com.secondpasslibrary.reader.library.LibraryAxis
import com.secondpasslibrary.reader.library.LibraryFailure
import com.secondpasslibrary.reader.library.LibraryResultKind
import com.secondpasslibrary.reader.library.LibraryState
import com.secondpasslibrary.reader.library.chrome.committedQuery
import com.secondpasslibrary.reader.library.chrome.orderingLabel
import com.secondpasslibrary.reader.library.chrome.resultCount
import com.secondpasslibrary.reader.library.presentation.LibraryPagingTriggerPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryAuthorSeriesPresentationTest {
    @Test
    fun `Author and Series cards map identity and book-count labels`() {
        val author = LibraryAuthor("a", "Author", "Author", "", 1, null)
        val series = LibrarySeries("s", "Series", "Series", "", 12, null)

        assertEquals("Author", author.toLibraryAuthorSeriesPresentation().name)
        assertEquals("1 book", author.toLibraryAuthorSeriesPresentation().bookCountLabel)
        assertEquals("Series", series.toLibraryAuthorSeriesPresentation().name)
        assertEquals("12 books", series.toLibraryAuthorSeriesPresentation().bookCountLabel)
    }

    @Test
    fun `preview omission empty return and populated return remain distinct`() {
        val omitted = authorWithPreviews(null).toLibraryAuthorSeriesPresentation().previews
        val empty = authorWithPreviews(emptyList()).toLibraryAuthorSeriesPresentation().previews
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
            ).toLibraryAuthorSeriesPresentation().previews

        assertEquals(LibraryAuthorSeriesPreviewBooksPresentation.Omitted, omitted)
        assertEquals(LibraryAuthorSeriesPreviewBooksPresentation.Returned(emptyList()), empty)
        val books = (populated as LibraryAuthorSeriesPreviewBooksPresentation.Returned).books
        assertEquals(null, books.first().cover)
        assertEquals(
            PublicBookCoverReference.fromAbsoluteUrl("https://covers.example/book.webp"),
            books.last().cover
        )
    }

    @Test
    fun `presentation preserves all preview covers returned by the bounded request`() {
        val previews = (
            authorWithPreviews(
                (1..4).map { LibraryPreviewBook("$it", "Book $it", null) }
            ).toLibraryAuthorSeriesPresentation().previews as
                LibraryAuthorSeriesPreviewBooksPresentation.Returned
            ).books

        assertEquals(listOf("1", "2", "3", "4"), previews.map { it.id })
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
                    PagedLibraryAxisState(
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
    fun `selected Author detail maps loading failure and content explicitly`() {
        val loading = PagedLibraryAxisDetailState<LibraryAuthor>("a", loading = true)
        val failure =
            PagedLibraryAxisDetailState<LibraryAuthor>(
                "a",
                failure = LibraryFailure.PROTOCOL_INVALID
            )
        val content =
            PagedLibraryAxisDetailState(
                "a",
                detail = LibraryAuthor("a", "Author", "Author", "Biography", 2, emptyList())
            )

        assertTrue(
            loading.toAuthorDetailPresentation() is LibraryAuthorSeriesDetailPresentation.Loading
        )
        assertTrue(
            failure.toAuthorDetailPresentation() is LibraryAuthorSeriesDetailPresentation.Failure
        )
        val presented =
            content.toAuthorDetailPresentation() as LibraryAuthorSeriesDetailPresentation.Content
        assertEquals("Biography", presented.description)
    }

    @Test
    fun `selected Series detail maps blank summary to absent description`() {
        val detail =
            PagedLibraryAxisDetailState(
                "s",
                detail = LibrarySeries("s", "Series", "Series", "  ", 3, emptyList())
            )

        val presented =
            detail.toSeriesDetailPresentation() as LibraryAuthorSeriesDetailPresentation.Content
        assertEquals(null, presented.description)
    }

    @Test
    fun `paging trigger remains based on proximity rather than axis type`() {
        assertTrue(
            LibraryPagingTriggerPolicy.shouldRequestNextPage(lastVisibleIndex = 14, itemCount = 20)
        )
        assertEquals(
            false,
            LibraryPagingTriggerPolicy.shouldRequestNextPage(lastVisibleIndex = 4, itemCount = 20)
        )
    }

    private fun authorWithPreviews(previews: List<LibraryPreviewBook>?) =
        LibraryAuthor("a", "Author", "Author", "", 2, previews)
}
