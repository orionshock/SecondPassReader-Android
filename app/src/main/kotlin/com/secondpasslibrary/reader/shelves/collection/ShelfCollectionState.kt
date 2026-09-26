package com.secondpasslibrary.reader.shelves.collection

import com.secondpasslibrary.client.Shelf
import com.secondpasslibrary.client.ShelfOrdering
import com.secondpasslibrary.reader.shelves.SHELVES_PAGE_SIZE
import com.secondpasslibrary.reader.shelves.ShelvesLoadError

internal data class ShelfCollectionState(
    val ordering: ShelfOrdering = ShelfOrdering.NAME,
    val pageSize: Int = SHELVES_PAGE_SIZE,
    val shelves: List<Shelf> = emptyList(),
    val totalCount: Int = 0,
    val hasLoaded: Boolean = false,
    val initialLoading: Boolean = false,
    val refreshing: Boolean = false,
    val nextPageLoading: Boolean = false,
    val error: ShelvesLoadError? = null,
    val hasNext: Boolean = false,
    val currentPage: Int = 0,
    val searchInput: String = "",
    val search: ShelfSearchState = ShelfSearchState()
) {
    val activeShelves: List<Shelf> get() = search.takeIf { it.query != null }?.shelves ?: shelves
    val activeTotalCount: Int get() = search.takeIf { it.query != null }?.totalCount ?: totalCount
    val activeHasLoaded: Boolean get() = search.takeIf { it.query != null }?.hasLoaded ?: hasLoaded
    val activeInitialLoading: Boolean
        get() = search.takeIf { it.query != null }?.initialLoading ?: initialLoading
    val activeRefreshing: Boolean get() = search.takeIf { it.query != null }?.refreshing
        ?: refreshing
    val activeNextPageLoading: Boolean
        get() = search.takeIf { it.query != null }?.nextPageLoading ?: nextPageLoading
    val activeError: ShelvesLoadError? get() = search.takeIf { it.query != null }?.error ?: error
    val activeHasNext: Boolean get() = search.takeIf { it.query != null }?.hasNext ?: hasNext
    val activeCurrentPage: Int get() = search.takeIf { it.query != null }?.currentPage
        ?: currentPage
}

internal data class ShelfSearchState(
    val query: String? = null,
    val shelves: List<Shelf> = emptyList(),
    val totalCount: Int = 0,
    val hasLoaded: Boolean = false,
    val initialLoading: Boolean = false,
    val refreshing: Boolean = false,
    val nextPageLoading: Boolean = false,
    val error: ShelvesLoadError? = null,
    val hasNext: Boolean = false,
    val currentPage: Int = 0
)

internal sealed interface ShelfCollectionChange {
    data class Added(val shelf: Shelf) : ShelfCollectionChange

    data class Updated(val shelf: Shelf) : ShelfCollectionChange

    data class Removed(val shelfId: String) : ShelfCollectionChange
}

internal sealed interface ShelfSearchIntent {
    data class Update(val value: String) : ShelfSearchIntent

    data object Submit : ShelfSearchIntent

    data object Clear : ShelfSearchIntent
}
