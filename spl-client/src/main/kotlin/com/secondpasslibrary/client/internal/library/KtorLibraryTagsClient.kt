package com.secondpasslibrary.client.internal.library

import com.secondpasslibrary.client.AuthenticatedLibraryTagsClient
import com.secondpasslibrary.client.CatalogTagListOptions
import com.secondpasslibrary.client.LibraryCatalogTag
import com.secondpasslibrary.client.LibraryPage
import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.client.internal.transport.AuthenticatedRequestExecutor
import com.secondpasslibrary.client.internal.transport.decodeProtocolBody
import io.ktor.client.call.body
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.json.Json

internal class KtorLibraryTagsClient(
    private val requests: AuthenticatedRequestExecutor,
    private val json: Json
) : AuthenticatedLibraryTagsClient {
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
        val response = requests.get(scope.path("tags/"), parameters)
        return json.decodeProtocolBody<LibraryCatalogTagPageWire>(
            response.body(),
            "catalog tag page"
        )
            .toModel(options.page, options.pageSize)
    }

    override suspend fun get(tagId: String): LibraryCatalogTag {
        require(tagId.isNotBlank()) { "Catalog tag ID must not be blank." }
        val response = requests.get("library/tags/${tagId.encodeURLPathPart()}/")
        return json.decodeProtocolBody<LibraryCatalogTagWire>(
            response.body(),
            "catalog tag"
        ).toModel()
    }
}
