package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.LibraryGroupSummary

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
    val failure: LibraryFailure? = null
)

internal data class LibraryState(
    val axis: LibraryAxis = LibraryAxis.BOOKS,
    val scope: LibraryScope = LibraryScope.AllLibrary,
    val advancedGroupsEnabled: Boolean = false,
    val groupSelector: LibraryGroupSelectorState = LibraryGroupSelectorState(),
    val books: LibraryBooksState = LibraryBooksState()
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
