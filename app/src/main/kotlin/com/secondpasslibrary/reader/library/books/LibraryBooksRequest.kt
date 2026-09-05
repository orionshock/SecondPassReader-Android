package com.secondpasslibrary.reader.library.books

import com.secondpasslibrary.client.AuthenticatedLibraryBooksClient
import com.secondpasslibrary.client.BookListOptions
import com.secondpasslibrary.client.CatalogResultPage
import com.secondpasslibrary.client.CompactBook
import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.client.LibrarySearchOptions

internal data class LibraryBooksRequest(
    val mode: LibraryBooksMode,
    val query: String,
    val ordering: LibraryBooksOrdering,
    val filter: LibraryBooksFilter?,
    val tagSlug: String?,
    val scope: LibraryScope,
    val page: Int,
    val pageSize: Int
) {
    suspend fun load(client: AuthenticatedLibraryBooksClient): CatalogResultPage<CompactBook> =
        when (mode) {
            LibraryBooksMode.BROWSE ->
                client.list(
                    scope,
                    BookListOptions(
                        q = query.takeIf(String::isNotBlank),
                        authorId = (filter as? LibraryBooksFilter.Author)?.id,
                        seriesId = (filter as? LibraryBooksFilter.Series)?.id,
                        tagSlug = tagSlug,
                        ordering = (ordering as LibraryBooksOrdering.Browse).value,
                        page = page,
                        pageSize = pageSize
                    )
                )

            LibraryBooksMode.BROAD_SEARCH ->
                client.search(
                    scope,
                    LibrarySearchOptions(
                        q = query,
                        tagSlug = tagSlug,
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
            state.tagSlug,
            scope,
            page,
            state.pageSize
        )
    }
}
