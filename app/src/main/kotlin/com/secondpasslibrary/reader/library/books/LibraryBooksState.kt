package com.secondpasslibrary.reader.library.books

import com.secondpasslibrary.client.BookOrdering
import com.secondpasslibrary.client.CompactBook
import com.secondpasslibrary.client.LibraryCatalogTag
import com.secondpasslibrary.client.LibrarySearchOrdering
import com.secondpasslibrary.reader.library.DEFAULT_LIBRARY_PAGE_SIZE
import com.secondpasslibrary.reader.library.LibraryFailure

internal enum class LibraryBooksMode {
    BROWSE,
    BROAD_SEARCH
}

internal enum class LibraryBooksLayout {
    LIST,
    GRID
}

internal sealed interface LibraryBooksFilter {
    data class Author(val id: String) : LibraryBooksFilter

    data class Series(val id: String) : LibraryBooksFilter
}

internal sealed interface LibraryBooksOrdering {
    data class Browse(val value: BookOrdering) : LibraryBooksOrdering

    data class BroadSearch(val value: LibrarySearchOrdering) : LibraryBooksOrdering
}

internal data class LibraryBooksState(
    val offlineDownloadedOnly: Boolean = false,
    val mode: LibraryBooksMode = LibraryBooksMode.BROWSE,
    val filter: LibraryBooksFilter? = null,
    val tagSlug: String? = null,
    val committedQuery: String = "",
    val ordering: LibraryBooksOrdering = LibraryBooksOrdering.Browse(BookOrdering.TITLE),
    val pageSize: Int = DEFAULT_LIBRARY_PAGE_SIZE,
    val layout: LibraryBooksLayout = LibraryBooksLayout.GRID,
    val books: List<CompactBook> = emptyList(),
    val contextualCatalogTags: List<LibraryCatalogTag> = emptyList(),
    val hasContextualCatalogTagsResponse: Boolean = false,
    val totalCount: Int = 0,
    val initialLoading: Boolean = true,
    val nextPageLoading: Boolean = false,
    val refreshing: Boolean = false,
    val error: LibraryBooksLoadError? = null,
    val hasNext: Boolean = false,
    val currentPage: Int = 0
)

internal data class LibraryBooksLoadError(
    val failure: LibraryFailure,
    val phase: LibraryBooksLoadPhase
)

internal enum class LibraryBooksLoadPhase {
    INITIAL,
    NEXT_PAGE,
    REFRESH
}
