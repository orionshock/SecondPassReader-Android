package com.secondpasslibrary.client.internal

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class CompactBookPageWire(
    val count: Int? = null,
    val next: String? = null,
    val previous: String? = null,
    val results: List<CompactBookWire>? = null
)

@Serializable
internal data class CompactBookWire(
    val id: String? = null,
    val title: String? = null,
    @SerialName("sort_title") val sortTitle: String? = null,
    val subtitle: String? = null,
    val authors: List<BookAuthorWire>? = null,
    val series: BookSeriesWire? = null,
    @SerialName("catalog_tags") val catalogTags: List<CatalogTagWire>? = null,
    val language: String? = null,
    val publisher: String? = null,
    @SerialName("published_year") val publishedYear: Int? = null,
    @SerialName("published_month") val publishedMonth: Int? = null,
    @SerialName("published_day") val publishedDay: Int? = null,
    @SerialName("published_date_precision") val publicationDatePrecision: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("file_format") val fileFormat: String? = null
)

@Serializable
internal data class BookAuthorWire(val id: String? = null, val name: String? = null)

@Serializable
internal data class BookSeriesWire(
    val id: String? = null,
    val name: String? = null,
    @SerialName("sort_name") val sortName: String? = null,
    @SerialName("series_index") val seriesIndex: String? = null
)

@Serializable
internal data class CatalogTagWire(
    val id: String? = null,
    val name: String? = null,
    val slug: String? = null
)
