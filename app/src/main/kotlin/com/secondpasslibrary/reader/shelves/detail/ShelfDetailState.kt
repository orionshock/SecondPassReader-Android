package com.secondpasslibrary.reader.shelves.detail

import com.secondpasslibrary.client.Shelf
import com.secondpasslibrary.client.ShelfItem
import com.secondpasslibrary.client.ShelfItemOrdering
import com.secondpasslibrary.reader.shelves.SHELVES_PAGE_SIZE
import com.secondpasslibrary.reader.shelves.ShelvesFailure
import com.secondpasslibrary.reader.shelves.ShelvesLoadError

internal data class ShelfDetailResourceState(
    val shelf: Shelf? = null,
    val loading: Boolean = false,
    val failure: ShelvesFailure? = null
)

internal data class ShelfItemsState(
    val ordering: ShelfItemOrdering = ShelfItemOrdering.POSITION,
    val layout: ShelfBooksLayout = ShelfBooksLayout.GRID,
    val pageSize: Int = SHELVES_PAGE_SIZE,
    val items: List<ShelfItem> = emptyList(),
    val totalCount: Int = 0,
    val initialLoading: Boolean = false,
    val nextPageLoading: Boolean = false,
    val error: ShelvesLoadError? = null,
    val hasNext: Boolean = false,
    val currentPage: Int = 0
)

internal enum class ShelfBooksLayout {
    LIST,
    GRID
}

internal data class ShelfDetailState(
    val shelfId: String? = null,
    val detail: ShelfDetailResourceState = ShelfDetailResourceState(),
    val items: ShelfItemsState = ShelfItemsState()
)
