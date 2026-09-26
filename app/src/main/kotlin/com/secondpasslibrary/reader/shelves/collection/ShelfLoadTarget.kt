package com.secondpasslibrary.reader.shelves.collection

import com.secondpasslibrary.client.Shelf
import com.secondpasslibrary.client.ShelfOrdering
import com.secondpasslibrary.client.ShelfPage
import com.secondpasslibrary.reader.shelves.ShelvesFailure
import com.secondpasslibrary.reader.shelves.ShelvesLoadError
import com.secondpasslibrary.reader.shelves.ShelvesLoadPhase

internal enum class ShelfLoadTarget { NORMAL, SEARCH }

internal val ShelfCollectionState.loadTarget: ShelfLoadTarget
    get() = if (search.query == null) ShelfLoadTarget.NORMAL else ShelfLoadTarget.SEARCH

internal fun ShelfCollectionState.withLoading(
    phase: ShelvesLoadPhase,
    target: ShelfLoadTarget
): ShelfCollectionState = if (target == ShelfLoadTarget.SEARCH) {
    copy(
        search = search.copy(
            initialLoading = phase == ShelvesLoadPhase.INITIAL && !search.hasLoaded,
            refreshing = phase == ShelvesLoadPhase.INITIAL && search.hasLoaded,
            nextPageLoading = phase == ShelvesLoadPhase.NEXT_PAGE,
            error = null
        )
    )
} else {
    copy(
        initialLoading = phase == ShelvesLoadPhase.INITIAL && !hasLoaded,
        refreshing = phase == ShelvesLoadPhase.INITIAL && hasLoaded,
        nextPageLoading = phase == ShelvesLoadPhase.NEXT_PAGE,
        error = null
    )
}

internal fun ShelfCollectionState.applyPage(
    page: ShelfPage,
    phase: ShelvesLoadPhase,
    target: ShelfLoadTarget
): ShelfCollectionState {
    if (target == ShelfLoadTarget.NORMAL) {
        return copy(
            shelves = shelves.updatedWith(page.shelves, phase),
            totalCount = page.totalCount,
            hasLoaded = true,
            initialLoading = false,
            refreshing = false,
            nextPageLoading = false,
            error = null,
            hasNext = page.hasNextPage,
            currentPage = page.page
        )
    }
    return copy(
        search = search.copy(
            shelves = search.shelves.updatedWith(page.shelves, phase),
            totalCount = page.totalCount,
            hasLoaded = true,
            initialLoading = false,
            refreshing = false,
            nextPageLoading = false,
            error = null,
            hasNext = page.hasNextPage,
            currentPage = page.page
        )
    )
}

internal fun ShelfCollectionState.applyFailure(
    failure: ShelvesFailure,
    phase: ShelvesLoadPhase,
    target: ShelfLoadTarget
): ShelfCollectionState = if (target == ShelfLoadTarget.SEARCH) {
    copy(
        search = search.copy(
            initialLoading = false,
            refreshing = false,
            nextPageLoading = false,
            error = ShelvesLoadError(failure, phase)
        )
    )
} else {
    copy(
        initialLoading = false,
        refreshing = false,
        nextPageLoading = false,
        error = ShelvesLoadError(failure, phase)
    )
}

internal fun ShelfCollectionState.apply(change: ShelfCollectionChange): Pair<List<Shelf>, Int> =
    when (change) {
        is ShelfCollectionChange.Added -> {
            val exists = shelves.any { it.id == change.shelf.id }
            shelves.filterNot { it.id == change.shelf.id } + change.shelf to
                totalCount + if (exists) 0 else 1
        }

        is ShelfCollectionChange.Updated ->
            shelves.map { if (it.id == change.shelf.id) change.shelf else it } to totalCount

        is ShelfCollectionChange.Removed -> {
            val retained = shelves.filterNot { it.id == change.shelfId }
            retained to (totalCount - (shelves.size - retained.size)).coerceAtLeast(0)
        }
    }

internal fun List<Shelf>.sortedFor(ordering: ShelfOrdering): List<Shelf> = when (ordering) {
    ShelfOrdering.NAME -> sortedBy { it.name.lowercase() }
    ShelfOrdering.NAME_DESCENDING -> sortedByDescending { it.name.lowercase() }
    ShelfOrdering.ITEM_COUNT -> sortedBy { it.itemCount }
    ShelfOrdering.ITEM_COUNT_DESCENDING -> sortedByDescending { it.itemCount }
}

private fun List<Shelf>.updatedWith(incoming: List<Shelf>, phase: ShelvesLoadPhase): List<Shelf> =
    if (phase == ShelvesLoadPhase.NEXT_PAGE) this + incoming else incoming
