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
