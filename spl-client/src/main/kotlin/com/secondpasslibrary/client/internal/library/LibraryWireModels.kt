package com.secondpasslibrary.client.internal.library

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class CompactBookPageWire(
    val count: Int? = null,
    val next: String? = null,
    val previous: String? = null,
    @SerialName("catalog_tags") val catalogTags: List<LibraryCatalogTagWire>? = null,
    val results: List<CompactBookWire>? = null
)

@Serializable
internal data class LibraryGroupPageWire(
    val count: Int? = null,
    val next: String? = null,
    val previous: String? = null,
    val results: List<LibraryGroupWire>? = null
)

@Serializable
internal data class LibraryGroupWire(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    @SerialName("is_public_group") val isPublicGroup: Boolean? = null
)

@Serializable
internal data class LibraryCatalogTagPageWire(
    val count: Int? = null,
    val next: String? = null,
    val previous: String? = null,
    val results: List<LibraryCatalogTagWire>? = null
)

@Serializable
internal data class LibraryCatalogTagWire(
    val id: String? = null,
    val name: String? = null,
    val slug: String? = null,
    @SerialName("book_count") val bookCount: Int? = null
)

@Serializable
internal data class LibraryAuthorPageWire(
    val count: Int? = null,
    val next: String? = null,
    val previous: String? = null,
    @SerialName("catalog_tags") val catalogTags: List<LibraryCatalogTagWire>? = null,
    val results: List<LibraryAuthorWire>? = null
)

@Serializable
internal data class LibrarySeriesPageWire(
    val count: Int? = null,
    val next: String? = null,
    val previous: String? = null,
    @SerialName("catalog_tags") val catalogTags: List<LibraryCatalogTagWire>? = null,
    val results: List<LibrarySeriesWire>? = null
)

@Serializable
internal data class LibraryAuthorWire(
    val id: String? = null,
    val name: String? = null,
    @SerialName("sort_name") val sortName: String? = null,
    val biography: String? = null,
    @SerialName("book_count") val bookCount: Int? = null,
    @SerialName("preview_books") val previewBooks: List<LibraryPreviewBookWire>? = null
)

@Serializable
internal data class LibrarySeriesWire(
    val id: String? = null,
    val name: String? = null,
    @SerialName("sort_name") val sortName: String? = null,
    val summary: String? = null,
    @SerialName("book_count") val bookCount: Int? = null,
    @SerialName("preview_books") val previewBooks: List<LibraryPreviewBookWire>? = null
)

@Serializable
internal data class LibraryPreviewBookWire(
    val id: String? = null,
    val title: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null
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
internal data class LibraryBookDetailWire(
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
    val description: String? = null,
    val identifiers: List<BookIdentifierWire>? = null,
    val file: BookFileWire? = null,
    val groups: List<BookGroupWire>? = null
)

@Serializable
internal data class BookIdentifierWire(
    val id: String? = null,
    val scheme: String? = null,
    val value: String? = null
)

@Serializable
internal data class BookFileWire(
    val format: String? = null,
    @SerialName("file_size") val fileSize: Long? = null,
    val checksum: String? = null,
    @SerialName("download_url") val downloadUrl: String? = null
)

@Serializable
internal data class BookGroupWire(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    @SerialName("is_public_group") val isPublicGroup: Boolean? = null
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
