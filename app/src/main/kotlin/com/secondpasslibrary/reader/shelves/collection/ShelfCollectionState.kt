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
    val currentPage: Int = 0
)

internal sealed interface ShelfCollectionChange {
    data class Added(val shelf: Shelf) : ShelfCollectionChange

    data class Updated(val shelf: Shelf) : ShelfCollectionChange

    data class Removed(val shelfId: String) : ShelfCollectionChange
}
