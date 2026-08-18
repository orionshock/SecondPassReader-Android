package com.secondpasslibrary.reader.library

internal data class LibraryEntityState<T, O>(
    val committedQuery: String = "",
    val ordering: O,
    val pageSize: Int = DEFAULT_LIBRARY_PAGE_SIZE,
    val items: List<T> = emptyList(),
    val totalCount: Int = 0,
    val initialLoading: Boolean = false,
    val nextPageLoading: Boolean = false,
    val error: LibraryEntityLoadError? = null,
    val hasNext: Boolean = false,
    val currentPage: Int = 0,
    val selected: LibraryEntityDetailState<T>? = null
)

internal data class LibraryEntityLoadError(
    val failure: LibraryFailure,
    val phase: LibraryEntityLoadPhase
)

internal enum class LibraryEntityLoadPhase {
    INITIAL,
    NEXT_PAGE
}

internal data class LibraryEntityDetailState<T>(
    val id: String,
    val detail: T? = null,
    val loading: Boolean = false,
    val failure: LibraryFailure? = null
)

internal const val LIBRARY_AXIS_PREVIEW_LIMIT = 3
