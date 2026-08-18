package com.secondpasslibrary.client

import com.secondpasslibrary.client.internal.BookAuthorWire
import com.secondpasslibrary.client.internal.BookSeriesWire
import com.secondpasslibrary.client.internal.CatalogTagWire
import com.secondpasslibrary.client.internal.CompactBookPageWire
import com.secondpasslibrary.client.internal.CompactBookWire
import com.secondpasslibrary.client.internal.LibraryGroupPageWire
import com.secondpasslibrary.client.internal.LibraryGroupWire

private const val COMPACT_BOOK_CONTEXT = "compact book"
private const val BOOK_PAGE_CONTEXT = "book page"
private const val GROUP_CONTEXT = "library group"
private const val GROUP_PAGE_CONTEXT = "library group page"

internal fun CompactBookPageWire.toModel(page: Int, pageSize: Int): LibraryPage<CompactBook> {
    val totalCount = count ?: invalidProtocol(BOOK_PAGE_CONTEXT)
    if (totalCount < 0) invalidProtocol(BOOK_PAGE_CONTEXT)
    return LibraryPage(
        totalCount = totalCount,
        results = results?.map(CompactBookWire::toModel) ?: invalidProtocol(BOOK_PAGE_CONTEXT),
        hasNext = next != null,
        hasPrevious = previous != null,
        page = page,
        pageSize = pageSize
    )
}

internal fun LibraryGroupPageWire.toModel(
    page: Int,
    pageSize: Int
): LibraryPage<LibraryGroupSummary> {
    val totalCount = count ?: invalidProtocol(GROUP_PAGE_CONTEXT)
    if (totalCount < 0) invalidProtocol(GROUP_PAGE_CONTEXT)
    return LibraryPage(
        totalCount = totalCount,
        results = results?.map(LibraryGroupWire::toModel) ?: invalidProtocol(GROUP_PAGE_CONTEXT),
        hasNext = next != null,
        hasPrevious = previous != null,
        page = page,
        pageSize = pageSize
    )
}

private fun LibraryGroupWire.toModel() = LibraryGroupSummary(
    id = id.required(GROUP_CONTEXT),
    name = name.required(GROUP_CONTEXT),
    isPublicGroup = isPublicGroup ?: invalidProtocol(GROUP_CONTEXT)
)

private fun CompactBookWire.toModel(): CompactBook = CompactBook(
    id = id.required(COMPACT_BOOK_CONTEXT),
    title = title.required(COMPACT_BOOK_CONTEXT),
    sortTitle = sortTitle.required(COMPACT_BOOK_CONTEXT),
    subtitle = subtitle ?: invalidProtocol(COMPACT_BOOK_CONTEXT),
    authors = authors?.map(BookAuthorWire::toModel) ?: invalidProtocol(COMPACT_BOOK_CONTEXT),
    series = series?.toModel(),
    catalogTags =
        catalogTags?.map(CatalogTagWire::toModel) ?: invalidProtocol(COMPACT_BOOK_CONTEXT),
    language = language,
    publisher = publisher,
    publishedYear = publishedYear,
    publishedMonth = publishedMonth,
    publishedDay = publishedDay,
    publicationDatePrecision = publicationDatePrecision.toPublicationPrecision(),
    cover = coverUrl?.let(PublicBookCoverReference::fromServer),
    fileFormat = fileFormat.required(COMPACT_BOOK_CONTEXT)
)

private fun BookAuthorWire.toModel(): BookAuthorSummary = BookAuthorSummary(
    id = id.required(COMPACT_BOOK_CONTEXT),
    name = name.required(COMPACT_BOOK_CONTEXT)
)

private fun BookSeriesWire.toModel(): BookSeriesSummary = BookSeriesSummary(
    id = id.required(COMPACT_BOOK_CONTEXT),
    name = name.required(COMPACT_BOOK_CONTEXT),
    sortName = sortName.required(COMPACT_BOOK_CONTEXT),
    seriesIndex = seriesIndex?.let(SeriesIndex::fromServer)
)

private fun CatalogTagWire.toModel(): CatalogTagSummary = CatalogTagSummary(
    id = id.required(COMPACT_BOOK_CONTEXT),
    name = name.required(COMPACT_BOOK_CONTEXT),
    slug = slug.required(COMPACT_BOOK_CONTEXT)
)

private fun String?.toPublicationPrecision(): PublicationDatePrecision = when (this) {
    "" -> PublicationDatePrecision.UNSPECIFIED
    "year" -> PublicationDatePrecision.YEAR
    "month" -> PublicationDatePrecision.MONTH
    "day" -> PublicationDatePrecision.DAY
    else -> invalidProtocol(COMPACT_BOOK_CONTEXT)
}
