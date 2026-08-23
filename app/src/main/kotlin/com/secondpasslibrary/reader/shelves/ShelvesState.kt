package com.secondpasslibrary.reader.shelves

import com.secondpasslibrary.reader.shelves.collection.ShelfCollectionState
import com.secondpasslibrary.reader.shelves.detail.ShelfDetailState
import com.secondpasslibrary.reader.shelves.editor.ShelfContentsEditorState

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

internal data class ShelfDetailEntry(val shelfId: String, val origin: ShelvesCollection)

internal data class ShelvesNavigationState(
    val destination: ShelvesDestination =
        ShelvesDestination.Collection(ShelvesCollection.PERSONAL),
    val createOpen: Boolean = false
)

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
internal const val SHELF_CARD_PREVIEW_LIMIT = 6
