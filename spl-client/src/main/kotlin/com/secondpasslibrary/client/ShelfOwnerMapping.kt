package com.secondpasslibrary.client

import com.secondpasslibrary.client.internal.ShelfOwnerGroupWire
import com.secondpasslibrary.client.internal.ShelfUserWire
import com.secondpasslibrary.client.internal.ShelfWire

internal fun ShelfWire.mapOwner(): ShelfOwner = when (ownerType.required(SHELF_CONTEXT)) {
    "user" -> ownerUser?.toOwnerModel() ?: invalidProtocol(SHELF_CONTEXT)
    "group" -> ownerGroup?.toModel() ?: invalidProtocol(SHELF_CONTEXT)
    else -> invalidProtocol(SHELF_CONTEXT)
}

private fun ShelfUserWire.toOwnerModel(): ShelfOwner.User = ShelfOwner.User(
    profileId = profileId.required(SHELF_CONTEXT),
    username = username
)

internal fun ShelfUserWire.toModel(): ShelfUser = ShelfUser(
    profileId = profileId.required(SHELF_CONTEXT),
    username = username
)

private fun ShelfOwnerGroupWire.toModel(): ShelfOwner.Group = ShelfOwner.Group(
    id = id.required(SHELF_CONTEXT),
    name = name.required(SHELF_CONTEXT),
    isPublicGroup = isPublicGroup ?: invalidProtocol(SHELF_CONTEXT)
)

internal fun String?.toShelfVisibility(): ShelfVisibility = when (this) {
    "private" -> ShelfVisibility.PRIVATE
    "listed" -> ShelfVisibility.LISTED
    else -> invalidProtocol(SHELF_CONTEXT)
}
