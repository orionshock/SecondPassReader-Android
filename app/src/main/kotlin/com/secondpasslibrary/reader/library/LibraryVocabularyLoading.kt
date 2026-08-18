package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.AuthenticatedLibraryClient
import com.secondpasslibrary.client.CatalogTagListOptions
import com.secondpasslibrary.client.CatalogTagOrdering
import com.secondpasslibrary.client.LibraryCatalogTag
import com.secondpasslibrary.client.LibraryGroupListOptions
import com.secondpasslibrary.client.LibraryGroupOrdering
import com.secondpasslibrary.client.LibraryGroupSummary
import com.secondpasslibrary.client.LibraryScope

internal suspend fun AuthenticatedLibraryClient.loadAllGroups(): List<LibraryGroupSummary> =
    buildList {
        var pageNumber = 1
        do {
            val page = groups.listGroups(
                LibraryGroupListOptions(
                    ordering = LibraryGroupOrdering.NAME,
                    page = pageNumber,
                    pageSize = MAX_SELECTOR_PAGE_SIZE
                )
            )
            addAll(page.results)
            pageNumber += 1
        } while (page.hasNext)
    }

internal suspend fun AuthenticatedLibraryClient.loadAllTags(
    scope: LibraryScope
): List<LibraryCatalogTag> = buildList {
    var pageNumber = 1
    do {
        val page = tags.list(
            scope,
            CatalogTagListOptions(
                ordering = CatalogTagOrdering.NAME,
                page = pageNumber,
                pageSize = MAX_SELECTOR_PAGE_SIZE
            )
        )
        addAll(page.results)
        pageNumber += 1
    } while (page.hasNext)
}

private const val MAX_SELECTOR_PAGE_SIZE = 200
