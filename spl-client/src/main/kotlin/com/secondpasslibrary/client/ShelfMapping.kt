package com.secondpasslibrary.client

import com.secondpasslibrary.client.internal.ShelfEditorPageWire
import com.secondpasslibrary.client.internal.ShelfItemPageWire
import com.secondpasslibrary.client.internal.ShelfItemWire
import com.secondpasslibrary.client.internal.ShelfPageWire
import com.secondpasslibrary.client.internal.ShelfPreviewBookWire
import com.secondpasslibrary.client.internal.ShelfWire

internal const val SHELF_CONTEXT = "shelf"
private const val SHELF_PAGE_CONTEXT = "shelf page"
private const val SHELF_ITEM_CONTEXT = "shelf item"
private const val SHELF_EDITOR_CONTEXT = "shelf editor page"

internal fun ShelfPageWire.toModel(page: Int, pageSize: Int): ShelfPage = ShelfPage(
    totalCount = validCount(count, SHELF_PAGE_CONTEXT),
    hasNextPage = next != null,
    hasPreviousPage = previous != null,
    shelves = results?.map(ShelfWire::toModel) ?: invalidProtocol(SHELF_PAGE_CONTEXT),
    page = page,
    pageSize = pageSize
)

internal fun ShelfWire.toModel(): Shelf = Shelf(
    id = id.required(SHELF_CONTEXT),
    name = name.required(SHELF_CONTEXT),
    description = description,
    owner = mapOwner(),
    visibility = visibility.toShelfVisibility(),
    itemCount = validCount(itemCount, SHELF_CONTEXT),
    canEdit = canEdit ?: invalidProtocol(SHELF_CONTEXT),
    createdBy = createdBy?.toModel(),
    createdAt = createdAt.required(SHELF_CONTEXT),
    updatedAt = updatedAt.required(SHELF_CONTEXT),
    matchedItemId = matchedItemId,
    previewBooks = previewBooks?.map(ShelfPreviewBookWire::toModel)
)

internal fun ShelfItemPageWire.toModel(page: Int, pageSize: Int): ShelfItemPage = ShelfItemPage(
    totalCount = validCount(count, SHELF_ITEM_CONTEXT),
    results = results?.map(ShelfItemWire::toItemModel) ?: invalidProtocol(SHELF_ITEM_CONTEXT),
    hasNext = next != null,
    hasPrevious = previous != null,
    page = page,
    pageSize = pageSize
)

internal fun ShelfEditorPageWire.toModel(page: Int, pageSize: Int): ShelfEditorPage =
    ShelfEditorPage(
        totalCount = validCount(count, SHELF_EDITOR_CONTEXT),
        visibleItemCount = validCount(visibleItemCount, SHELF_EDITOR_CONTEXT),
        unavailableItemCount = validCount(unavailableItemCount, SHELF_EDITOR_CONTEXT),
        results = results?.map(ShelfItemWire::toEditorModel)
            ?: invalidProtocol(SHELF_EDITOR_CONTEXT),
        hasNext = next != null,
        hasPrevious = previous != null,
        page = page,
        pageSize = pageSize
    )

private fun ShelfPreviewBookWire.toModel(): ShelfPreviewBook = ShelfPreviewBook(
    id = id.required(SHELF_CONTEXT),
    title = title.required(SHELF_CONTEXT),
    cover = coverUrl?.let(PublicBookCoverReference::fromServer)
)

private fun ShelfItemWire.toItemModel(): ShelfItem = ShelfItem(
    id = id.required(SHELF_ITEM_CONTEXT),
    shelfId = shelf.required(SHELF_ITEM_CONTEXT),
    book = book?.toModel() ?: invalidProtocol(SHELF_ITEM_CONTEXT),
    position = position ?: invalidProtocol(SHELF_ITEM_CONTEXT),
    addedBy = addedBy?.toModel(),
    createdAt = createdAt.required(SHELF_ITEM_CONTEXT),
    updatedAt = updatedAt.required(SHELF_ITEM_CONTEXT)
)

private fun ShelfItemWire.toEditorModel(): ShelfEditorItem {
    val itemId = id.required(SHELF_EDITOR_CONTEXT)
    val shelfId = shelf.required(SHELF_EDITOR_CONTEXT)
    val storedPosition = position ?: invalidProtocol(SHELF_EDITOR_CONTEXT)
    val actor = addedBy?.toModel()
    val visibleBook = book
    return if (visibleBook == null) {
        ShelfEditorItem.Unavailable(itemId, shelfId, storedPosition, actor)
    } else {
        ShelfEditorItem.Available(
            id = itemId,
            shelfId = shelfId,
            position = storedPosition,
            addedBy = actor,
            book = visibleBook.toModel(),
            createdAt = createdAt.required(SHELF_EDITOR_CONTEXT),
            updatedAt = updatedAt.required(SHELF_EDITOR_CONTEXT)
        )
    }
}

private fun validCount(value: Int?, context: String): Int {
    val count = value ?: invalidProtocol(context)
    if (count < 0) invalidProtocol(context)
    return count
}
