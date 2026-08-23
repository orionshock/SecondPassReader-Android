package com.secondpasslibrary.reader.library.axis

import com.secondpasslibrary.reader.library.DEFAULT_LIBRARY_PAGE_SIZE
import com.secondpasslibrary.reader.library.LibraryFailure

internal data class PagedLibraryAxisState<T, O>(
    val committedQuery: String = "",
    val ordering: O,
    val pageSize: Int = DEFAULT_LIBRARY_PAGE_SIZE,
    val items: List<T> = emptyList(),
    val totalCount: Int = 0,
    val initialLoading: Boolean = false,
    val nextPageLoading: Boolean = false,
    val error: PagedLibraryAxisLoadError? = null,
    val hasNext: Boolean = false,
    val currentPage: Int = 0,
    val selected: PagedLibraryAxisDetailState<T>? = null
)

internal data class PagedLibraryAxisLoadError(
    val failure: LibraryFailure,
    val phase: PagedLibraryAxisLoadPhase
)

internal enum class PagedLibraryAxisLoadPhase {
    INITIAL,
    NEXT_PAGE
}

internal data class PagedLibraryAxisDetailState<T>(
    val id: String,
    val detail: T? = null,
    val loading: Boolean = false,
    val failure: LibraryFailure? = null
)

internal const val LIBRARY_AXIS_PREVIEW_LIMIT = 24
internal const val LIBRARY_ENTITY_DETAIL_PREVIEW_LIMIT = 0
