package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.BookOrdering
import com.secondpasslibrary.client.CompactBook
import com.secondpasslibrary.client.LibraryGroupSummary
import com.secondpasslibrary.client.LibrarySearchOrdering

internal enum class LibraryBooksMode {
    BROWSE,
    BROAD_SEARCH
}

internal enum class LibraryBooksLayout {
    LIST,
    GRID
}

internal enum class LibraryAxis {
    BOOKS,
    AUTHORS,
    SERIES
}

internal sealed interface LibraryScope {
    data object AllLibrary : LibraryScope

    data class Group(val id: String) : LibraryScope
}

internal data class LibraryGroupSelectorState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val groups: List<LibraryGroupSummary> = emptyList(),
    val failure: LibraryBooksFailure? = null
)

internal sealed interface LibraryBooksOrdering {
    data class Browse(val value: BookOrdering) : LibraryBooksOrdering

    data class BroadSearch(val value: LibrarySearchOrdering) : LibraryBooksOrdering
}

internal data class LibraryBooksState(
    val mode: LibraryBooksMode = LibraryBooksMode.BROWSE,
    val committedQuery: String = "",
    val ordering: LibraryBooksOrdering = LibraryBooksOrdering.Browse(BookOrdering.TITLE),
    val pageSize: Int = DEFAULT_LIBRARY_PAGE_SIZE,
    val layout: LibraryBooksLayout = LibraryBooksLayout.GRID,
    val axis: LibraryAxis = LibraryAxis.BOOKS,
    val scope: LibraryScope = LibraryScope.AllLibrary,
    val advancedGroupsEnabled: Boolean = false,
    val groupSelector: LibraryGroupSelectorState = LibraryGroupSelectorState(),
    val books: List<CompactBook> = emptyList(),
    val totalCount: Int = 0,
    val initialLoading: Boolean = true,
    val nextPageLoading: Boolean = false,
    val refreshing: Boolean = false,
    val error: LibraryBooksLoadError? = null,
    val hasNext: Boolean = false,
    val currentPage: Int = 0
)

internal data class LibraryBooksLoadError(
    val failure: LibraryBooksFailure,
    val phase: LibraryBooksLoadPhase
)

internal enum class LibraryBooksFailure {
    UNREACHABLE,
    AUTHENTICATION_REJECTED,
    PROTOCOL_INVALID,
    OTHER
}

internal enum class LibraryBooksLoadPhase {
    INITIAL,
    NEXT_PAGE,
    REFRESH
}

internal sealed interface LibraryBooksConnectionEvent {
    data object AuthenticationRejected : LibraryBooksConnectionEvent
}

internal const val DEFAULT_LIBRARY_PAGE_SIZE = 50
