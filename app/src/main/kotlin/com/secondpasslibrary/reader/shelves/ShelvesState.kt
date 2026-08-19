package com.secondpasslibrary.reader.shelves

import com.secondpasslibrary.client.Shelf
import com.secondpasslibrary.client.ShelfEditorItem
import com.secondpasslibrary.client.ShelfItem
import com.secondpasslibrary.client.ShelfItemOrdering
import com.secondpasslibrary.client.ShelfOrdering
import com.secondpasslibrary.client.ShelfVisibility

internal enum class ShelvesCollection {
    PERSONAL,
    SHARED,
    GROUP
}

internal sealed interface ShelvesDestination {
    data class Collection(val collection: ShelvesCollection) : ShelvesDestination

    data class Detail(val shelfId: String, val origin: ShelvesCollection) : ShelvesDestination

    data class ContentsEditor(val shelfId: String, val origin: ShelvesCollection) :
        ShelvesDestination
}

internal data class ShelfBookNavigationRequest(
    val bookId: String,
    val shelfId: String,
    val origin: ShelvesCollection
)

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

internal sealed interface ShelfCollectionChange {
    data class Added(val shelf: Shelf) : ShelfCollectionChange

    data class Updated(val shelf: Shelf) : ShelfCollectionChange

    data class Removed(val shelfId: String) : ShelfCollectionChange
}

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

internal data class ShelfContentsEditorState(
    val shelfId: String? = null,
    val entries: List<ShelfEditorItem> = emptyList(),
    val totalCount: Int = 0,
    val visibleItemCount: Int = 0,
    val unavailableItemCount: Int = 0,
    val pageSize: Int = SHELVES_PAGE_SIZE,
    val initialLoading: Boolean = false,
    val nextPageLoading: Boolean = false,
    val loadError: ShelvesLoadError? = null,
    val hasNext: Boolean = false,
    val currentPage: Int = 0,
    val mutation: ShelfContentsMutationState = ShelfContentsMutationState(),
    val positionDialog: ShelfPositionDialogState? = null,
    val removalDialog: ShelfRemovalDialogState? = null
) {
    val directPositionAvailable: Boolean
        get() = currentPage > 0 && unavailableItemCount == 0
}

internal data class ShelfContentsMutationState(
    val itemId: String? = null,
    val operation: ShelfContentsMutation? = null,
    val failure: ShelfContentsMutationFailure? = null
) {
    val inProgress: Boolean
        get() = itemId != null && operation != null
}

internal enum class ShelfContentsMutation {
    MOVE_UP,
    MOVE_DOWN,
    SET_POSITION,
    REMOVE
}

internal enum class ShelfContentsMutationFailure {
    DIRECT_POSITION_UNAVAILABLE,
    VALIDATION,
    AUTHENTICATION_REJECTED,
    NOT_AUTHORIZED,
    NOT_FOUND,
    UNREACHABLE,
    RECONCILE_FAILED,
    OTHER
}

internal data class ShelfPositionDialogState(
    val itemId: String,
    val title: String,
    val value: String,
    val invalid: Boolean = false
)

internal data class ShelfRemovalDialogState(val itemId: String, val label: String?)

internal data class ShelvesState(
    val destination: ShelvesDestination =
        ShelvesDestination.Collection(ShelvesCollection.PERSONAL),
    val personal: ShelfCollectionState = ShelfCollectionState(),
    val shared: ShelfCollectionState = ShelfCollectionState(),
    val group: ShelfCollectionState = ShelfCollectionState(),
    val detail: ShelfDetailState = ShelfDetailState(),
    val editor: ShelfContentsEditorState = ShelfContentsEditorState(),
    val createOpen: Boolean = false
)

internal data class CreatePersonalShelfState(
    val name: String = "",
    val description: String = "",
    val visibility: ShelfVisibility = ShelfVisibility.PRIVATE,
    val submitting: Boolean = false,
    val nameError: CreateShelfFieldError? = null,
    val descriptionError: CreateShelfFieldError? = null,
    val visibilityError: CreateShelfFieldError? = null,
    val failure: CreateShelfFailure? = null
)

internal enum class CreateShelfFieldError {
    REQUIRED,
    TOO_LONG,
    SERVER_REJECTED
}

internal enum class CreateShelfFailure {
    UNREACHABLE,
    AUTHENTICATION_REJECTED,
    REJECTED,
    FIELD_VALIDATION,
    OTHER
}

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
