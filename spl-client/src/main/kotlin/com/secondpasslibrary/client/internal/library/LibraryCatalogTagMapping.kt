package com.secondpasslibrary.client.internal.library

import com.secondpasslibrary.client.LibraryCatalogTag
import com.secondpasslibrary.client.LibraryPage
import com.secondpasslibrary.client.internal.transport.invalidProtocol
import com.secondpasslibrary.client.internal.transport.required

private const val TAG_CONTEXT = "catalog tag"
private const val TAG_PAGE_CONTEXT = "catalog tag page"

internal fun LibraryCatalogTagPageWire.toModel(
    page: Int,
    pageSize: Int
): LibraryPage<LibraryCatalogTag> {
    val totalCount = count.validTagCount(TAG_PAGE_CONTEXT)
    return LibraryPage(
        totalCount = totalCount,
        results = results?.map(LibraryCatalogTagWire::toModel) ?: invalidProtocol(TAG_PAGE_CONTEXT),
        hasNext = next != null,
        hasPrevious = previous != null,
        page = page,
        pageSize = pageSize
    )
}

internal fun LibraryCatalogTagWire.toModel(): LibraryCatalogTag = LibraryCatalogTag(
    id = id.required(TAG_CONTEXT),
    name = name.required(TAG_CONTEXT),
    slug = slug.required(TAG_CONTEXT),
    bookCount = bookCount.validTagCount(TAG_CONTEXT)
)

private fun Int?.validTagCount(context: String): Int {
    val value = this ?: invalidProtocol(context)
    return value.takeIf { it >= 0 } ?: invalidProtocol(context)
}
