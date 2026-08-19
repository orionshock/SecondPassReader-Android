package com.secondpasslibrary.client.internal

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ShelfPageWire(
    val count: Int? = null,
    val next: String? = null,
    val previous: String? = null,
    val results: List<ShelfWire>? = null
)

@Serializable
internal data class ShelfWire(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    @SerialName("owner_type") val ownerType: String? = null,
    @SerialName("owner_user") val ownerUser: ShelfUserWire? = null,
    @SerialName("owner_group") val ownerGroup: ShelfOwnerGroupWire? = null,
    val visibility: String? = null,
    @SerialName("item_count") val itemCount: Int? = null,
    @SerialName("can_edit") val canEdit: Boolean? = null,
    @SerialName("created_by") val createdBy: ShelfUserWire? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("matched_item_id") val matchedItemId: String? = null,
    @SerialName("preview_books") val previewBooks: List<ShelfPreviewBookWire>? = null
)

@Serializable
internal data class ShelfUserWire(
    @SerialName("profile_id") val profileId: String? = null,
    val username: String? = null
)

@Serializable
internal data class ShelfOwnerGroupWire(
    val id: String? = null,
    val name: String? = null,
    @SerialName("is_public_group") val isPublicGroup: Boolean? = null
)

@Serializable
internal data class ShelfPreviewBookWire(
    val id: String? = null,
    val title: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null
)

@Serializable
internal data class ShelfItemPageWire(
    val count: Int? = null,
    val next: String? = null,
    val previous: String? = null,
    val results: List<ShelfItemWire>? = null
)

@Serializable
internal data class ShelfItemWire(
    val id: String? = null,
    val shelf: String? = null,
    val book: CompactBookWire? = null,
    val position: Int? = null,
    val unavailable: Boolean? = null,
    @SerialName("added_by") val addedBy: ShelfUserWire? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
internal data class ShelfEditorPageWire(
    val count: Int? = null,
    val next: String? = null,
    val previous: String? = null,
    @SerialName("visible_item_count") val visibleItemCount: Int? = null,
    @SerialName("unavailable_item_count") val unavailableItemCount: Int? = null,
    val results: List<ShelfItemWire>? = null
)

@Serializable
internal data class CreateShelfWire(
    val name: String,
    val description: String,
    val visibility: String,
    @SerialName("owner_type") val ownerType: String
)

@Serializable
internal data class UpdateShelfWire(
    val name: String? = null,
    val description: String? = null,
    val visibility: String? = null
)

@Serializable
internal data class AddShelfItemWire(val book: String, val position: Int? = null)

@Serializable
internal data class MoveShelfItemWire(val move: String)

@Serializable
internal data class PositionShelfItemWire(val position: Int)
