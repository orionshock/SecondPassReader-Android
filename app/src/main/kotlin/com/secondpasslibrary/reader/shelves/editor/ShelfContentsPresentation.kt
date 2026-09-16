package com.secondpasslibrary.reader.shelves.editor

internal fun shelfEditorCountsLabel(visible: Int, unavailable: Int): String =
    "$visible available · $unavailable unavailable"

internal fun userFacingShelfPosition(storedPosition: Int): Int = storedPosition + 1

internal fun shouldRequestEditorNextPage(lastVisibleIndex: Int, itemCount: Int): Boolean =
    itemCount > 0 && lastVisibleIndex >= itemCount - EDITOR_PREFETCH_DISTANCE

internal fun ShelfContentsMutationFailure.message(): String = when (this) {
    ShelfContentsMutationFailure.DIRECT_POSITION_UNAVAILABLE ->
        "Choose Move up or Move down while some Shelf items are unavailable."

    ShelfContentsMutationFailure.VALIDATION ->
        "Second Pass Library rejected that change. Check the position and try again."

    ShelfContentsMutationFailure.AUTHENTICATION_REJECTED ->
        "Your connection is no longer authorized. Repair it in Settings."

    ShelfContentsMutationFailure.NOT_AUTHORIZED -> "You can’t change this Shelf."

    ShelfContentsMutationFailure.NOT_FOUND -> "That Shelf item is no longer available."

    ShelfContentsMutationFailure.UNREACHABLE ->
        "Couldn’t reach the Library. Check your connection and try again."

    ShelfContentsMutationFailure.RECONCILE_FAILED ->
        "The change may be saved, but the Shelf couldn’t be refreshed. Reopen it to check."

    ShelfContentsMutationFailure.OTHER -> "Couldn’t change the Shelf item. Try again."
}

private const val EDITOR_PREFETCH_DISTANCE = 5
