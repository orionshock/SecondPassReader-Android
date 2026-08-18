package com.secondpasslibrary.client

import com.secondpasslibrary.client.internal.LibraryCatalogTagPageWire
import com.secondpasslibrary.client.internal.LibraryCatalogTagWire
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
        return json.decodeLibrary<LibraryCatalogTagPageWire>(response.body(), "catalog tag page")
            .toModel(options.page, options.pageSize)
    }

    override suspend fun get(tagId: String): LibraryCatalogTag {
        require(tagId.isNotBlank()) { "Catalog tag ID must not be blank." }
        val response = requests.get("library/tags/${tagId.encodeURLPathPart()}/")
        return json.decodeLibrary<LibraryCatalogTagWire>(response.body(), "catalog tag").toModel()
    }
}
