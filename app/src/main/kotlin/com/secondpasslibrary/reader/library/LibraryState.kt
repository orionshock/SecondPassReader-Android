package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.AuthorOrdering
import com.secondpasslibrary.client.LibraryCatalogTag
import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.client.SeriesOrdering
import com.secondpasslibrary.reader.library.axis.LibraryAuthorsState
import com.secondpasslibrary.reader.library.axis.LibrarySeriesState
import com.secondpasslibrary.reader.library.axis.PagedLibraryAxisState
import com.secondpasslibrary.reader.library.books.LibraryBooksState
import com.secondpasslibrary.reader.library.chrome.LibraryGroupSelectorState
import com.secondpasslibrary.reader.library.chrome.LibraryTagSelectorState

internal enum class LibraryAxis {
    BOOKS,
    AUTHORS,
    SERIES
}

internal enum class LibraryAuthorityMode {
    ONLINE,
    OFFLINE
}

internal enum class LibraryResultKind {
    BOOKS,
    AUTHOR_INDEX,
    SERIES_INDEX;

    val supportsBookLayout: Boolean
        get() = this == BOOKS
}

internal data class LibraryState(
    val axis: LibraryAxis = LibraryAxis.BOOKS,
    val resultKind: LibraryResultKind = LibraryResultKind.BOOKS,
    val scope: LibraryScope = LibraryScope.Global,
    val advancedGroupsEnabled: Boolean = false,
    val groupSelector: LibraryGroupSelectorState = LibraryGroupSelectorState(),
    val selectedTag: LibraryCatalogTag? = null,
    val tagSelector: LibraryTagSelectorState = LibraryTagSelectorState(),
    val books: LibraryBooksState = LibraryBooksState(),
    val authors: LibraryAuthorsState = PagedLibraryAxisState(ordering = AuthorOrdering.NAME),
    val series: LibrarySeriesState = PagedLibraryAxisState(ordering = SeriesOrdering.NAME)
)

internal enum class LibraryFailure {
    UNREACHABLE,
    AUTHENTICATION_REJECTED,
    PROTOCOL_INVALID,
    OTHER
}

internal sealed interface LibraryConnectionEvent {
    data object AuthenticationRejected : LibraryConnectionEvent
}

internal const val DEFAULT_LIBRARY_PAGE_SIZE = 50
