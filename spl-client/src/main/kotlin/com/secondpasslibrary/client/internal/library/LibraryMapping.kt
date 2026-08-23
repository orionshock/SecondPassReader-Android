package com.secondpasslibrary.client.internal.library

import com.secondpasslibrary.client.BookAuthorSummary
import com.secondpasslibrary.client.BookSeriesSummary
import com.secondpasslibrary.client.CatalogTagSummary
import com.secondpasslibrary.client.CompactBook
import com.secondpasslibrary.client.LibraryGroupSummary
import com.secondpasslibrary.client.LibraryPage
import com.secondpasslibrary.client.PublicBookCoverReference
import com.secondpasslibrary.client.PublicationDatePrecision
import com.secondpasslibrary.client.SeriesIndex
import com.secondpasslibrary.client.internal.transport.invalidProtocol
import com.secondpasslibrary.client.internal.transport.required

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

internal fun CompactBookWire.toModel(): CompactBook = CompactBook(
    id = id.required(COMPACT_BOOK_CONTEXT),
    title = title.required(COMPACT_BOOK_CONTEXT),
    sortTitle = sortTitle.required(COMPACT_BOOK_CONTEXT),
    subtitle = subtitle ?: invalidProtocol(COMPACT_BOOK_CONTEXT),
    authors = authors?.map { it.toModel() } ?: invalidProtocol(COMPACT_BOOK_CONTEXT),
    series = series?.toModel(),
    catalogTags =
        catalogTags?.map { it.toModel() } ?: invalidProtocol(COMPACT_BOOK_CONTEXT),
    language = language,
    publisher = publisher,
    publishedYear = publishedYear,
    publishedMonth = publishedMonth,
    publishedDay = publishedDay,
    publicationDatePrecision = publicationDatePrecision.toPublicationPrecision(),
    cover = coverUrl?.let(PublicBookCoverReference::fromServer),
    fileFormat = fileFormat.required(COMPACT_BOOK_CONTEXT)
)

internal fun BookAuthorWire.toModel(context: String = COMPACT_BOOK_CONTEXT): BookAuthorSummary =
    BookAuthorSummary(
        id = id.required(context),
        name = name.required(context)
    )

internal fun BookSeriesWire.toModel(context: String = COMPACT_BOOK_CONTEXT): BookSeriesSummary =
    BookSeriesSummary(
        id = id.required(context),
        name = name.required(context),
        sortName = sortName.required(context),
        seriesIndex = seriesIndex?.let(SeriesIndex::fromServer)
    )

internal fun CatalogTagWire.toModel(context: String = COMPACT_BOOK_CONTEXT): CatalogTagSummary =
    CatalogTagSummary(
        id = id.required(context),
        name = name.required(context),
        slug = slug.required(context)
    )

internal fun String?.toPublicationPrecision(
    context: String = COMPACT_BOOK_CONTEXT
): PublicationDatePrecision = when (this) {
    "" -> PublicationDatePrecision.UNSPECIFIED
    "year" -> PublicationDatePrecision.YEAR
    "month" -> PublicationDatePrecision.MONTH
    "day" -> PublicationDatePrecision.DAY
    else -> invalidProtocol(context)
}
