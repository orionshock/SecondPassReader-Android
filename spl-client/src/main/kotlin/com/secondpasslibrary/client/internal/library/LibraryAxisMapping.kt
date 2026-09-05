package com.secondpasslibrary.client.internal.library

import com.secondpasslibrary.client.CatalogResultPage
import com.secondpasslibrary.client.LibraryAuthor
import com.secondpasslibrary.client.LibraryPreviewBook
import com.secondpasslibrary.client.LibrarySeries
import com.secondpasslibrary.client.PublicBookCoverReference
import com.secondpasslibrary.client.internal.transport.invalidProtocol
import com.secondpasslibrary.client.internal.transport.required

private const val AUTHOR_CONTEXT = "library author"
private const val AUTHOR_PAGE_CONTEXT = "library author page"
private const val SERIES_CONTEXT = "library series"
private const val SERIES_PAGE_CONTEXT = "library series page"
private const val PREVIEW_BOOK_CONTEXT = "library preview book"

internal fun LibraryAuthorPageWire.toModel(
    page: Int,
    pageSize: Int
): CatalogResultPage<LibraryAuthor> =
    toCatalogResultPage(page, pageSize, AUTHOR_PAGE_CONTEXT, LibraryAuthorWire::toModel)

internal fun LibrarySeriesPageWire.toModel(
    page: Int,
    pageSize: Int
): CatalogResultPage<LibrarySeries> =
    toCatalogResultPage(page, pageSize, SERIES_PAGE_CONTEXT, LibrarySeriesWire::toModel)

internal fun LibraryAuthorWire.toModel(): LibraryAuthor {
    val count = bookCount.validCount(AUTHOR_CONTEXT)
    return LibraryAuthor(
        id = id.required(AUTHOR_CONTEXT),
        name = name.required(AUTHOR_CONTEXT),
        sortName = sortName.required(AUTHOR_CONTEXT),
        biography = biography ?: invalidProtocol(AUTHOR_CONTEXT),
        bookCount = count,
        previewBooks = previewBooks?.map(LibraryPreviewBookWire::toModel)
    )
}

internal fun LibrarySeriesWire.toModel(): LibrarySeries {
    val count = bookCount.validCount(SERIES_CONTEXT)
    return LibrarySeries(
        id = id.required(SERIES_CONTEXT),
        name = name.required(SERIES_CONTEXT),
        sortName = sortName.required(SERIES_CONTEXT),
        summary = summary ?: invalidProtocol(SERIES_CONTEXT),
        bookCount = count,
        previewBooks = previewBooks?.map(LibraryPreviewBookWire::toModel)
    )
}

private fun LibraryPreviewBookWire.toModel(): LibraryPreviewBook = LibraryPreviewBook(
    id = id.required(PREVIEW_BOOK_CONTEXT),
    title = title.required(PREVIEW_BOOK_CONTEXT),
    cover = coverUrl?.let(PublicBookCoverReference::fromServer)
)

private fun Int?.validCount(context: String): Int {
    val count = this ?: invalidProtocol(context)
    return count.takeIf { it >= 0 } ?: invalidProtocol(context)
}

private fun <W, T> LibraryAxisPageWire<W>.toCatalogResultPage(
    page: Int,
    pageSize: Int,
    context: String,
    mapper: (W) -> T
): CatalogResultPage<T> {
    val totalCount = count.validCount(context)
    return CatalogResultPage(
        totalCount = totalCount,
        results = results?.map(mapper) ?: invalidProtocol(context),
        catalogTags =
            catalogTags?.map(LibraryCatalogTagWire::toModel) ?: invalidProtocol(context),
        hasNext = next != null,
        hasPrevious = previous != null,
        page = page,
        pageSize = pageSize
    )
}

private data class LibraryAxisPageWire<W>(
    val count: Int?,
    val next: String?,
    val previous: String?,
    val catalogTags: List<LibraryCatalogTagWire>?,
    val results: List<W>?
)

private fun <T> LibraryAuthorPageWire.toCatalogResultPage(
    page: Int,
    pageSize: Int,
    context: String,
    mapper: (LibraryAuthorWire) -> T
): CatalogResultPage<T> = LibraryAxisPageWire(count, next, previous, catalogTags, results)
    .toCatalogResultPage(page, pageSize, context, mapper)

private fun <T> LibrarySeriesPageWire.toCatalogResultPage(
    page: Int,
    pageSize: Int,
    context: String,
    mapper: (LibrarySeriesWire) -> T
): CatalogResultPage<T> = LibraryAxisPageWire(count, next, previous, catalogTags, results)
    .toCatalogResultPage(page, pageSize, context, mapper)
