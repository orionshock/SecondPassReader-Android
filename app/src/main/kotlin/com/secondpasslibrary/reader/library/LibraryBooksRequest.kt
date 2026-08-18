package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.AuthenticatedLibraryBooksClient
import com.secondpasslibrary.client.BookListOptions
import com.secondpasslibrary.client.BookOrdering
import com.secondpasslibrary.client.CompactBook
import com.secondpasslibrary.client.GroupBookListOptions
import com.secondpasslibrary.client.LibraryPage
import com.secondpasslibrary.client.LibrarySearchOptions
import com.secondpasslibrary.client.LibrarySearchOrdering

internal data class LibraryBooksRequest(
    val mode: LibraryBooksMode,
    val query: String,
    val ordering: LibraryBooksOrdering,
    val filter: LibraryBooksFilter?,
    val scope: LibraryScope,
    val page: Int,
    val pageSize: Int
) {
    suspend fun load(client: AuthenticatedLibraryBooksClient): LibraryPage<CompactBook> =
        when (val selectedScope = scope) {
            is LibraryScope.Group ->
                client.listGroupBooks(
                    selectedScope.id,
                    GroupBookListOptions(
                        q = query.takeIf(String::isNotBlank),
                        authorId = (filter as? LibraryBooksFilter.Author)?.id,
                        seriesId = (filter as? LibraryBooksFilter.Series)?.id,
                        ordering = ordering.toBookOrdering(),
                        page = page,
                        pageSize = pageSize
                    )
                )

            LibraryScope.AllLibrary -> loadAllLibrary(client)
        }

    private suspend fun loadAllLibrary(
        client: AuthenticatedLibraryBooksClient
    ): LibraryPage<CompactBook> = when (mode) {
        LibraryBooksMode.BROWSE ->
            client.listBooks(
                BookListOptions(
                    q = query.takeIf(String::isNotBlank),
                    authorId = (filter as? LibraryBooksFilter.Author)?.id,
                    seriesId = (filter as? LibraryBooksFilter.Series)?.id,
                    ordering = (ordering as LibraryBooksOrdering.Browse).value,
                    page = page,
                    pageSize = pageSize
                )
            )

        LibraryBooksMode.BROAD_SEARCH ->
            client.searchLibrary(
                LibrarySearchOptions(
                    q = query,
                    ordering = (ordering as LibraryBooksOrdering.BroadSearch).value,
                    page = page,
                    pageSize = pageSize
                )
            )
    }

    companion object {
        fun from(state: LibraryBooksState, scope: LibraryScope, page: Int) = LibraryBooksRequest(
            state.mode,
            state.committedQuery,
            state.ordering,
            state.filter,
            scope,
            page,
            state.pageSize
        )
    }
}

private fun LibraryBooksOrdering.toBookOrdering(): BookOrdering = when (this) {
    is LibraryBooksOrdering.Browse -> value

    is LibraryBooksOrdering.BroadSearch ->
        when (value) {
            LibrarySearchOrdering.TITLE -> BookOrdering.TITLE
            LibrarySearchOrdering.TITLE_DESCENDING -> BookOrdering.TITLE_DESCENDING
            LibrarySearchOrdering.AUTHOR -> BookOrdering.AUTHOR
            LibrarySearchOrdering.AUTHOR_DESCENDING -> BookOrdering.AUTHOR_DESCENDING
            LibrarySearchOrdering.SERIES -> BookOrdering.SERIES
            LibrarySearchOrdering.SERIES_DESCENDING -> BookOrdering.SERIES_DESCENDING
        }
}
