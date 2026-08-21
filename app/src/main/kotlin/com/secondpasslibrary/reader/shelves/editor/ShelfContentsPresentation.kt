package com.secondpasslibrary.reader.shelves.editor

internal fun shelfEditorCountsLabel(visible: Int, unavailable: Int): String =
    "$visible visible / $unavailable unavailable"

internal fun userFacingShelfPosition(storedPosition: Int): Int = storedPosition + 1

internal fun shouldRequestEditorNextPage(lastVisibleIndex: Int, itemCount: Int): Boolean =
    itemCount > 0 && lastVisibleIndex >= itemCount - EDITOR_PREFETCH_DISTANCE

internal fun ShelfContentsMutationFailure.message(): String = when (this) {
    ShelfContentsMutationFailure.DIRECT_POSITION_UNAVAILABLE ->
        "Exact positioning is unavailable while retained shelf items cannot be viewed."

    ShelfContentsMutationFailure.VALIDATION -> "The server rejected that shelf item change."

    ShelfContentsMutationFailure.AUTHENTICATION_REJECTED ->
        "Your connection is no longer authorized."

    ShelfContentsMutationFailure.NOT_AUTHORIZED -> "This shelf cannot be changed by this client."

    ShelfContentsMutationFailure.NOT_FOUND -> "That shelf item no longer exists."

    ShelfContentsMutationFailure.UNREACHABLE -> "The server could not be reached."

    ShelfContentsMutationFailure.RECONCILE_FAILED ->
        "The change may be saved, but the shelf could not be refreshed."

    ShelfContentsMutationFailure.OTHER -> "The shelf item could not be changed."
}

private const val EDITOR_PREFETCH_DISTANCE = 5
