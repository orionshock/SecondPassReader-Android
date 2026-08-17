package com.secondpasslibrary.client

import com.secondpasslibrary.client.internal.ReadingProgressWire
import com.secondpasslibrary.client.internal.RecentReadingBookWire
import com.secondpasslibrary.client.internal.RecentReadingItemWire
import com.secondpasslibrary.client.internal.RecentReadingResponseWire
import com.secondpasslibrary.client.internal.ShelfOwnerGroupWire
import com.secondpasslibrary.client.internal.ShelfOwnerUserWire
import com.secondpasslibrary.client.internal.ShelfPageWire
import com.secondpasslibrary.client.internal.ShelfPreviewBookWire
import com.secondpasslibrary.client.internal.ShelfWire

private const val RECENT_READING_CONTEXT = "recent reading"
private const val SHELF_LIST_CONTEXT = "shelf list"

internal fun RecentReadingResponseWire.toModel(): List<RecentReadingItem> =
    results?.map(RecentReadingItemWire::toModel) ?: invalidProtocol(RECENT_READING_CONTEXT)

private fun RecentReadingItemWire.toModel(): RecentReadingItem = RecentReadingItem(
    sessionId = id.required(RECENT_READING_CONTEXT),
    sessionName = name ?: invalidProtocol(RECENT_READING_CONTEXT),
    status =
        when (status) {
            "active" -> ReadingSessionStatus.ACTIVE
            "closed" -> ReadingSessionStatus.CLOSED
            else -> invalidProtocol(RECENT_READING_CONTEXT)
        },
    lastActivityAt = lastActivityAt.required(RECENT_READING_CONTEXT),
    book = book?.toModel() ?: invalidProtocol(RECENT_READING_CONTEXT),
    progress = progress?.toModel()
)

private fun RecentReadingBookWire.toModel(): RecentReadingBook = RecentReadingBook(
    id = id.required(RECENT_READING_CONTEXT),
    title = title.required(RECENT_READING_CONTEXT),
    cover = coverUrl?.let(PublicBookCoverReference::fromServer),
    canOpen = canOpen ?: invalidProtocol(RECENT_READING_CONTEXT)
)

private fun ReadingProgressWire.toModel(): ReadingProgress = ReadingProgress(
    cfi = cfi.required(RECENT_READING_CONTEXT),
    locationLabel = locationLabel.required(RECENT_READING_CONTEXT),
    updatedAt = updatedAt.required(RECENT_READING_CONTEXT)
)

internal fun ShelfPageWire.toModel(): ShelfPage {
    val count = count ?: invalidProtocol(SHELF_LIST_CONTEXT)
    if (count < 0) invalidProtocol(SHELF_LIST_CONTEXT)
    return ShelfPage(
        totalCount = count,
        hasNextPage = next != null,
        hasPreviousPage = previous != null,
        shelves = results?.map(ShelfWire::toModel) ?: invalidProtocol(SHELF_LIST_CONTEXT)
    )
}

private fun ShelfWire.toModel(): ShelfSummary {
    val count = itemCount ?: invalidProtocol(SHELF_LIST_CONTEXT)
    if (count < 0) invalidProtocol(SHELF_LIST_CONTEXT)
    return ShelfSummary(
        id = id.required(SHELF_LIST_CONTEXT),
        name = name.required(SHELF_LIST_CONTEXT),
        description = description,
        owner = mapOwner(),
        visibility = visibility.required(SHELF_LIST_CONTEXT),
        itemCount = count,
        canEdit = canEdit ?: invalidProtocol(SHELF_LIST_CONTEXT),
        previewBooks = previewBooks?.map(ShelfPreviewBookWire::toModel)
    )
}

private fun ShelfWire.mapOwner(): ShelfOwner {
    val type = ownerType.required(SHELF_LIST_CONTEXT)
    return when (type) {
        "user" -> ownerUser?.toModel() ?: invalidProtocol(SHELF_LIST_CONTEXT)
        "group" -> ownerGroup?.toModel() ?: invalidProtocol(SHELF_LIST_CONTEXT)
        else -> ShelfOwner.Other(type)
    }
}

private fun ShelfOwnerUserWire.toModel(): ShelfOwner.User = ShelfOwner.User(
    profileId = profileId.required(SHELF_LIST_CONTEXT),
    username = username,
    firstName = firstName,
    lastName = lastName
)

private fun ShelfOwnerGroupWire.toModel(): ShelfOwner.Group = ShelfOwner.Group(
    id = id.required(SHELF_LIST_CONTEXT),
    name = name.required(SHELF_LIST_CONTEXT),
    isPublicGroup = isPublicGroup
)

private fun ShelfPreviewBookWire.toModel(): ShelfPreviewBook = ShelfPreviewBook(
    id = id.required(SHELF_LIST_CONTEXT),
    title = title.required(SHELF_LIST_CONTEXT),
    cover = coverUrl?.let(PublicBookCoverReference::fromServer)
)
