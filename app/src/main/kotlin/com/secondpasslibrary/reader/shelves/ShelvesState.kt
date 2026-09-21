package com.secondpasslibrary.reader.shelves

import com.secondpasslibrary.client.ShelfItemMove
import com.secondpasslibrary.client.ShelfItemOrdering
import com.secondpasslibrary.client.ShelfOrdering
import com.secondpasslibrary.client.ShelfVisibility
import com.secondpasslibrary.reader.shelves.collection.ShelfCollectionState
import com.secondpasslibrary.reader.shelves.detail.ShelfBooksLayout
import com.secondpasslibrary.reader.shelves.detail.ShelfDetailState
import com.secondpasslibrary.reader.shelves.editor.ShelfContentsEditorState
import com.secondpasslibrary.reader.shelves.management.CreatePersonalShelfState
import com.secondpasslibrary.reader.shelves.management.DeletePersonalShelfState
import com.secondpasslibrary.reader.shelves.management.EditPersonalShelfState

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
    val create: CreatePersonalShelfState = CreatePersonalShelfState(),
    val edit: EditPersonalShelfState = EditPersonalShelfState(),
    val delete: DeletePersonalShelfState = DeletePersonalShelfState(),
    val createOpen: Boolean = false
)

internal sealed interface ShelvesIntent {
    data class ShowCollection(val collection: ShelvesCollection) : ShelvesIntent
    data class SelectShelf(val shelfId: String) : ShelvesIntent
    data class OpenShelf(val entry: ShelfDetailEntry) : ShelvesIntent
    data object BackFromDetail : ShelvesIntent
    data class ChangeCollectionOrdering(val ordering: ShelfOrdering) : ShelvesIntent
    data class LoadNextCollectionPage(val collection: ShelvesCollection) : ShelvesIntent
    data class RetryCollection(val collection: ShelvesCollection) : ShelvesIntent

    data object OpenCreate : ShelvesIntent
    data object DismissCreate : ShelvesIntent
    data class UpdateCreateName(val value: String) : ShelvesIntent
    data class UpdateCreateDescription(val value: String) : ShelvesIntent
    data class UpdateCreateVisibility(val value: ShelfVisibility) : ShelvesIntent
    data object SubmitCreate : ShelvesIntent

    data object OpenEdit : ShelvesIntent
    data object DismissEdit : ShelvesIntent
    data class UpdateEditName(val value: String) : ShelvesIntent
    data class UpdateEditDescription(val value: String) : ShelvesIntent
    data class UpdateEditVisibility(val value: ShelfVisibility) : ShelvesIntent
    data object SubmitEdit : ShelvesIntent

    data object OpenDelete : ShelvesIntent
    data object DismissDelete : ShelvesIntent
    data object ConfirmDelete : ShelvesIntent

    data object OpenContentsEditor : ShelvesIntent
    data object BackFromContentsEditor : ShelvesIntent
    data object LoadNextEditorPage : ShelvesIntent
    data object RetryEditor : ShelvesIntent
    data class MoveEditorItem(val itemId: String, val direction: ShelfItemMove) : ShelvesIntent
    data class OpenEditorPosition(val itemId: String) : ShelvesIntent
    data class UpdateEditorPosition(val value: String) : ShelvesIntent
    data object SubmitEditorPosition : ShelvesIntent
    data object DismissEditorPosition : ShelvesIntent
    data class RequestEditorRemoval(val itemId: String) : ShelvesIntent
    data object ConfirmEditorRemoval : ShelvesIntent
    data object DismissEditorRemoval : ShelvesIntent
    data object DismissEditorMutationFailure : ShelvesIntent

    data class ChangeItemOrdering(val ordering: ShelfItemOrdering) : ShelvesIntent
    data class SetItemLayout(val layout: ShelfBooksLayout) : ShelvesIntent
    data object LoadNextItemPage : ShelvesIntent
    data object RetryDetail : ShelvesIntent
    data object RetryItems : ShelvesIntent
    data object LeaveMutationSurfaces : ShelvesIntent
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
internal const val SHELF_CARD_PREVIEW_LIMIT = 24
