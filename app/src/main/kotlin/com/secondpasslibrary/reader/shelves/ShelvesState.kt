package com.secondpasslibrary.reader.shelves

import com.secondpasslibrary.client.Shelf
import com.secondpasslibrary.client.ShelfItem
import com.secondpasslibrary.client.ShelfItemOrdering
import com.secondpasslibrary.client.ShelfOrdering

internal enum class ShelvesCollection {
    PERSONAL,
    SHARED
}

internal sealed interface ShelvesDestination {
    data class Collection(val collection: ShelvesCollection) : ShelvesDestination

    data class Detail(val shelfId: String, val origin: ShelvesCollection) : ShelvesDestination
}

internal data class ShelfCollectionState(
    val ordering: ShelfOrdering = ShelfOrdering.NAME,
    val pageSize: Int = SHELVES_PAGE_SIZE,
    val shelves: List<Shelf> = emptyList(),
    val totalCount: Int = 0,
    val initialLoading: Boolean = false,
    val nextPageLoading: Boolean = false,
    val error: ShelvesLoadError? = null,
    val hasNext: Boolean = false,
    val currentPage: Int = 0
)

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

internal data class ShelvesState(
    val destination: ShelvesDestination =
        ShelvesDestination.Collection(ShelvesCollection.PERSONAL),
    val personal: ShelfCollectionState = ShelfCollectionState(),
    val shared: ShelfCollectionState = ShelfCollectionState(),
    val detail: ShelfDetailState = ShelfDetailState()
)

internal data class ShelvesLoadError(val failure: ShelvesFailure, val phase: ShelvesLoadPhase)

internal enum class ShelvesLoadPhase {
    INITIAL,
    NEXT_PAGE
}

internal enum class ShelvesFailure {
    UNREACHABLE,
    AUTHENTICATION_REJECTED,
    PROTOCOL_INVALID,
    OTHER
}

internal sealed interface ShelvesConnectionEvent {
    data object AuthenticationRejected : ShelvesConnectionEvent
}

internal const val SHELVES_PAGE_SIZE = 50
internal const val SHELF_CARD_PREVIEW_LIMIT = 3
