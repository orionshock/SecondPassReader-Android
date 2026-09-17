package com.secondpasslibrary.client.internal.library

import com.secondpasslibrary.client.AuthenticatedLibraryTagsClient
import com.secondpasslibrary.client.CatalogTagListOptions
import com.secondpasslibrary.client.LibraryCatalogTag
import com.secondpasslibrary.client.LibraryPage
import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.client.internal.transport.AuthenticatedRequestExecutor
import io.ktor.http.encodeURLPathPart

internal class KtorLibraryTagsClient(private val requests: AuthenticatedRequestExecutor) :
    AuthenticatedLibraryTagsClient {
    override suspend fun list(
        scope: LibraryScope,
        options: CatalogTagListOptions
    ): LibraryPage<LibraryCatalogTag> {
        val parameters = buildList {
            options.q?.let { add("q" to it) }
            add("ordering" to options.ordering.queryValue)
            add("page" to options.page.toString())
            add("page_size" to options.pageSize.toString())
        }
        return requests.getDecoded<LibraryCatalogTagPageWire>(
            scope.path("tags/"),
            parameters,
            "catalog tag page"
        )
            .toModel(options.page, options.pageSize)
    }

    override suspend fun get(tagId: String): LibraryCatalogTag {
        require(tagId.isNotBlank()) { "Catalog tag ID must not be blank." }
        return requests.getDecoded<LibraryCatalogTagWire>(
            "library/tags/${tagId.encodeURLPathPart()}/",
            context = "catalog tag"
        ).toModel()
    }
}
