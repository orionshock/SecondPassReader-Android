package com.secondpasslibrary.client.internal.library

import com.secondpasslibrary.client.AuthenticatedLibraryAuthorsClient
import com.secondpasslibrary.client.AuthorListOptions
import com.secondpasslibrary.client.CatalogResultPage
import com.secondpasslibrary.client.LibraryAuthor
import com.secondpasslibrary.client.LibraryEntityDetailOptions
import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.client.internal.transport.AuthenticatedRequestExecutor
import com.secondpasslibrary.client.internal.transport.previewParameters
import io.ktor.http.encodeURLPathPart

internal class KtorLibraryAuthorsClient(private val requests: AuthenticatedRequestExecutor) :
    AuthenticatedLibraryAuthorsClient {
    override suspend fun list(
        scope: LibraryScope,
        options: AuthorListOptions
    ): CatalogResultPage<LibraryAuthor> = listAt(scope.path("authors/"), options)

    override suspend fun getAuthor(
        authorId: String,
        options: LibraryEntityDetailOptions
    ): LibraryAuthor {
        require(authorId.isNotBlank()) { "Author ID must not be blank." }
        return requests.getDecoded<LibraryAuthorWire>(
            "library/authors/${authorId.encodeURLPathPart()}/",
            previewParameters(options.previewLimit),
            "library author"
        ).toModel()
    }

    private suspend fun listAt(
        path: String,
        options: AuthorListOptions
    ): CatalogResultPage<LibraryAuthor> {
        val parameters = buildList {
            options.q?.let { add("q" to it) }
            options.tagSlug?.let { add("tag" to it) }
            add("ordering" to options.ordering.queryValue)
            add("page" to options.page.toString())
            add("page_size" to options.pageSize.toString())
            addAll(previewParameters(options.previewLimit))
        }
        return requests.getDecoded<LibraryAuthorPageWire>(path, parameters, "library author page")
            .toModel(options.page, options.pageSize)
    }
}
