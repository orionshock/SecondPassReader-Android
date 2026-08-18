package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.AuthorOrdering
import com.secondpasslibrary.client.LibraryCatalogTag
import com.secondpasslibrary.client.LibraryGroupSummary
import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.client.SeriesOrdering

internal enum class LibraryAxis {
    BOOKS,
    AUTHORS,
    SERIES
}

internal enum class LibraryResultKind {
    BOOKS,
    AUTHOR_INDEX,
    SERIES_INDEX;

    val supportsBookLayout: Boolean
        get() = this == BOOKS
}

internal data class LibraryGroupSelectorState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val groups: List<LibraryGroupSummary> = emptyList(),
    val failure: LibraryFailure? = null
)

internal data class LibraryTagSelectorState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val tags: List<LibraryCatalogTag> = emptyList(),
    val failure: LibraryFailure? = null
)

internal data class LibraryState(
    val axis: LibraryAxis = LibraryAxis.BOOKS,
    val resultKind: LibraryResultKind = LibraryResultKind.BOOKS,
    val scope: LibraryScope = LibraryScope.Global,
    val advancedGroupsEnabled: Boolean = false,
    val groupSelector: LibraryGroupSelectorState = LibraryGroupSelectorState(),
    val selectedTag: LibraryCatalogTag? = null,
    val tagSelector: LibraryTagSelectorState = LibraryTagSelectorState(),
    val books: LibraryBooksState = LibraryBooksState(),
    val authors: LibraryAuthorsState = LibraryEntityState(ordering = AuthorOrdering.NAME),
    val series: LibrarySeriesState = LibraryEntityState(ordering = SeriesOrdering.NAME)
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
