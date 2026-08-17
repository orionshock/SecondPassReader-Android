package com.secondpasslibrary.client.internal

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class RecentReadingResponseWire(val results: List<RecentReadingItemWire>? = null)

@Serializable
internal data class RecentReadingItemWire(
    val id: String? = null,
    val name: String? = null,
    val status: String? = null,
    @SerialName("last_activity_at") val lastActivityAt: String? = null,
    val book: RecentReadingBookWire? = null,
    val progress: ReadingProgressWire? = null
)

@Serializable
internal data class RecentReadingBookWire(
    val id: String? = null,
    val title: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("can_open") val canOpen: Boolean? = null
)

@Serializable
internal data class ReadingProgressWire(
    val cfi: String? = null,
    @SerialName("location_label") val locationLabel: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

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
    @SerialName("owner_user") val ownerUser: ShelfOwnerUserWire? = null,
    @SerialName("owner_group") val ownerGroup: ShelfOwnerGroupWire? = null,
    val visibility: String? = null,
    @SerialName("item_count") val itemCount: Int? = null,
    @SerialName("can_edit") val canEdit: Boolean? = null,
    @SerialName("preview_books") val previewBooks: List<ShelfPreviewBookWire>? = null
)

@Serializable
internal data class ShelfOwnerUserWire(
    @SerialName("profile_id") val profileId: String? = null,
    val username: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null
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
