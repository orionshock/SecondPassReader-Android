package com.secondpasslibrary.reader.shelves

import com.secondpasslibrary.client.Shelf
import com.secondpasslibrary.client.ShelfItemOrdering
import com.secondpasslibrary.client.ShelfOrdering
import com.secondpasslibrary.client.ShelfOwner
import com.secondpasslibrary.client.ShelfPreviewBook
import com.secondpasslibrary.client.ShelfVisibility
import com.secondpasslibrary.reader.design.components.AppBarNavigation
import com.secondpasslibrary.reader.design.components.AppBarPresentation

internal enum class ShelfOwnerKind {
    PERSONAL,
    SHARED_USER,
    GROUP
}

internal sealed interface ShelfPreviewPresentation {
    data object Absent : ShelfPreviewPresentation

    data object Empty : ShelfPreviewPresentation

    data class Books(val books: List<ShelfPreviewBook>) : ShelfPreviewPresentation
}

internal data class ShelfCardPresentation(
    val id: String,
    val name: String,
    val ownerLabel: String?,
    val ownerKind: ShelfOwnerKind,
    val visibilityLabel: String?,
    val itemCount: Int,
    val itemCountLabel: String,
    val canEdit: Boolean,
    val previews: ShelfPreviewPresentation
)

internal data class ShelfOrderingOption(val value: ShelfOrdering, val label: String)

internal data class ShelfItemOrderingOption(val value: ShelfItemOrdering, val label: String)

internal fun ShelvesState.appBarPresentation(): AppBarPresentation = when (destination) {
    is ShelvesDestination.Collection -> {
        val collection = when (destination.collection) {
            ShelvesCollection.PERSONAL -> personal
            ShelvesCollection.SHARED -> shared
            ShelvesCollection.GROUP -> group
        }
        AppBarPresentation(
            AppBarNavigation.MENU,
            title = "Shelves",
            metadata = collection.activeTotalCount.shelfCountLabel
        )
    }

    is ShelvesDestination.Detail ->
        AppBarPresentation(
            AppBarNavigation.BACK,
            context = "Shelves",
            title = detail.detail.shelf?.name ?: "Shelf",
            metadata = detail.detail.shelf?.itemCount?.bookCountLabel
        )

    is ShelvesDestination.ContentsEditor ->
        AppBarPresentation(
            AppBarNavigation.BACK,
            context = detail.detail.shelf?.name ?: "Shelf",
            title = "Edit Shelf contents"
        )
}

internal fun Shelf.toCardPresentation() = ShelfCardPresentation(
    id = id,
    name = name,
    ownerLabel = owner.displayLabel(canEdit),
    ownerKind = owner.kind(canEdit),
    visibilityLabel = visibility.label.takeIf { canEdit },
    itemCount = itemCount,
    itemCountLabel = itemCount.bookCountLabel,
    canEdit = canEdit,
    previews = previewBooks.toPreviewPresentation()
)

private fun List<ShelfPreviewBook>?.toPreviewPresentation(): ShelfPreviewPresentation = when {
    this == null -> ShelfPreviewPresentation.Absent
    isEmpty() -> ShelfPreviewPresentation.Empty
    else -> ShelfPreviewPresentation.Books(this)
}

internal val shelfOrderingOptions =
    listOf(
        ShelfOrderingOption(ShelfOrdering.NAME, "Name A-Z"),
        ShelfOrderingOption(ShelfOrdering.NAME_DESCENDING, "Name Z-A"),
        ShelfOrderingOption(ShelfOrdering.ITEM_COUNT_DESCENDING, "Most books"),
        ShelfOrderingOption(ShelfOrdering.ITEM_COUNT, "Fewest books")
    )

internal val shelfItemOrderingOptions =
    listOf(
        ShelfItemOrderingOption(ShelfItemOrdering.POSITION, "Shelf order"),
        ShelfItemOrderingOption(ShelfItemOrdering.POSITION_DESCENDING, "Reverse shelf order"),
        ShelfItemOrderingOption(ShelfItemOrdering.TITLE, "Title A-Z"),
        ShelfItemOrderingOption(ShelfItemOrdering.TITLE_DESCENDING, "Title Z-A"),
        ShelfItemOrderingOption(ShelfItemOrdering.AUTHOR, "Author A-Z"),
        ShelfItemOrderingOption(ShelfItemOrdering.AUTHOR_DESCENDING, "Author Z-A")
    )

internal fun ShelfOrdering.label(): String = shelfOrderingOptions.first { it.value == this }.label

internal fun ShelfItemOrdering.label(): String =
    shelfItemOrderingOptions.first { it.value == this }.label

internal fun shouldRequestShelfNextPage(
    lastVisibleIndex: Int,
    itemCount: Int,
    prefetchDistance: Int = 6
): Boolean = itemCount > 0 && lastVisibleIndex >= (itemCount - prefetchDistance).coerceAtLeast(0)

private fun ShelfOwner.displayLabel(canEdit: Boolean): String? = when (this) {
    is ShelfOwner.Group -> "Group \u00b7 $name"

    is ShelfOwner.User -> when {
        canEdit -> null
        username.isNullOrBlank() -> "Shared by another reader"
        else -> "Shared by @$username"
    }
}

private fun ShelfOwner.kind(canEdit: Boolean): ShelfOwnerKind = when (this) {
    is ShelfOwner.Group -> ShelfOwnerKind.GROUP
    is ShelfOwner.User -> if (canEdit) ShelfOwnerKind.PERSONAL else ShelfOwnerKind.SHARED_USER
}

private val ShelfVisibility.label: String
    get() = when (this) {
        ShelfVisibility.PRIVATE -> "Private"
        ShelfVisibility.LISTED -> "Listed"
    }

internal val Int.bookCountLabel: String
    get() = "$this ${if (this == 1) "book" else "books"}"

internal val Int.shelfCountLabel: String
    get() = "$this ${if (this == 1) "shelf" else "shelves"}"

internal val ShelfCardPresentation.ownerContextLabel: String?
    get() = when (ownerKind) {
        ShelfOwnerKind.PERSONAL -> visibilityLabel
        ShelfOwnerKind.SHARED_USER, ShelfOwnerKind.GROUP -> ownerLabel
    }
