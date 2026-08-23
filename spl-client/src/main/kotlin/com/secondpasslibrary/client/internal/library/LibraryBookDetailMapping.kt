package com.secondpasslibrary.client.internal.library

import com.secondpasslibrary.client.AuthenticatedBookDownloadReference
import com.secondpasslibrary.client.BookFile
import com.secondpasslibrary.client.BookGroup
import com.secondpasslibrary.client.BookIdentifier
import com.secondpasslibrary.client.LibraryBookDetail
import com.secondpasslibrary.client.PublicBookCoverReference
import com.secondpasslibrary.client.internal.transport.invalidProtocol
import com.secondpasslibrary.client.internal.transport.required

private const val BOOK_DETAIL_CONTEXT = "book detail"

internal fun LibraryBookDetailWire.toModel(): LibraryBookDetail = LibraryBookDetail(
    id = id.required(BOOK_DETAIL_CONTEXT),
    title = title.required(BOOK_DETAIL_CONTEXT),
    sortTitle = sortTitle.required(BOOK_DETAIL_CONTEXT),
    subtitle = subtitle ?: invalidProtocol(BOOK_DETAIL_CONTEXT),
    authors = authors?.map { it.toModel(BOOK_DETAIL_CONTEXT) }
        ?: invalidProtocol(BOOK_DETAIL_CONTEXT),
    series = series?.toModel(BOOK_DETAIL_CONTEXT),
    language = language,
    publisher = publisher,
    publishedYear = publishedYear,
    publishedMonth = publishedMonth,
    publishedDay = publishedDay,
    publicationDatePrecision = publicationDatePrecision.toPublicationPrecision(BOOK_DETAIL_CONTEXT),
    cover = coverUrl?.let(PublicBookCoverReference::fromServer),
    description = description ?: invalidProtocol(BOOK_DETAIL_CONTEXT),
    identifiers = identifiers?.map(BookIdentifierWire::toModel)
        ?: invalidProtocol(BOOK_DETAIL_CONTEXT),
    catalogTags = catalogTags?.map { it.toModel(BOOK_DETAIL_CONTEXT) }
        ?: invalidProtocol(BOOK_DETAIL_CONTEXT),
    file = file?.toModel(),
    groups = groups?.map(BookGroupWire::toModel) ?: invalidProtocol(BOOK_DETAIL_CONTEXT)
)

private fun BookIdentifierWire.toModel() = BookIdentifier(
    id = id.required(BOOK_DETAIL_CONTEXT),
    scheme = scheme.required(BOOK_DETAIL_CONTEXT),
    value = value.required(BOOK_DETAIL_CONTEXT)
)

private fun BookFileWire.toModel(): BookFile {
    val size = fileSize ?: invalidProtocol(BOOK_DETAIL_CONTEXT)
    if (size < 0) invalidProtocol(BOOK_DETAIL_CONTEXT)
    return BookFile(
        format = format.required(BOOK_DETAIL_CONTEXT),
        fileSize = size,
        checksum = checksum?.takeIf(String::isNotBlank),
        download = AuthenticatedBookDownloadReference.fromServer(
            downloadUrl.required(BOOK_DETAIL_CONTEXT)
        )
    )
}

private fun BookGroupWire.toModel() = BookGroup(
    id = id.required(BOOK_DETAIL_CONTEXT),
    name = name.required(BOOK_DETAIL_CONTEXT),
    description = description ?: invalidProtocol(BOOK_DETAIL_CONTEXT),
    isPublicGroup = isPublicGroup ?: invalidProtocol(BOOK_DETAIL_CONTEXT)
)
