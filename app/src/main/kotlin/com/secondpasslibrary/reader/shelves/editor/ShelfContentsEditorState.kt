package com.secondpasslibrary.reader.shelves.editor

import com.secondpasslibrary.client.ShelfEditorItem
import com.secondpasslibrary.reader.shelves.SHELVES_PAGE_SIZE
import com.secondpasslibrary.reader.shelves.ShelvesLoadError

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
