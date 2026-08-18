package com.secondpasslibrary.client

import com.secondpasslibrary.client.internal.LibraryAuthorPageWire
import com.secondpasslibrary.client.internal.LibraryAuthorWire
import io.ktor.client.call.body
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.json.Json

internal class KtorLibraryAuthorsClient(
    private val requests: AuthenticatedRequestExecutor,
    private val json: Json
) : AuthenticatedLibraryAuthorsClient {
    override suspend fun list(
        scope: LibraryScope,
        options: AuthorListOptions
    ): LibraryPage<LibraryAuthor> = listAt(scope.path("authors/"), options)

    override suspend fun getAuthor(
        authorId: String,
        options: LibraryEntityDetailOptions
    ): LibraryAuthor {
        require(authorId.isNotBlank()) { "Author ID must not be blank." }
        val response = requests.get(
            "library/authors/${authorId.encodeURLPathPart()}/",
            previewParameters(options.previewLimit)
        )
        return json.decodeLibrary<LibraryAuthorWire>(response.body(), "library author").toModel()
    }

    private suspend fun listAt(
        path: String,
        options: AuthorListOptions
    ): LibraryPage<LibraryAuthor> {
        val parameters = buildList {
            options.q?.let { add("q" to it) }
            add("ordering" to options.ordering.queryValue)
            add("page" to options.page.toString())
            add("page_size" to options.pageSize.toString())
            addAll(previewParameters(options.previewLimit))
        }
        val response = requests.get(path, parameters)
        return json.decodeLibrary<LibraryAuthorPageWire>(response.body(), "library author page")
            .toModel(options.page, options.pageSize)
    }
}
