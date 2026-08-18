package com.secondpasslibrary.client

import com.secondpasslibrary.client.internal.CompactBookPageWire
import com.secondpasslibrary.client.internal.LibraryBookDetailWire
import io.ktor.client.call.body
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.json.Json

internal class KtorLibraryBooksClient(
    private val requests: AuthenticatedRequestExecutor,
    private val json: Json
) : AuthenticatedLibraryBooksClient {
    override suspend fun getBook(bookId: String): LibraryBookDetail {
        require(bookId.isNotBlank()) { "Book ID must not be blank." }
        val response = requests.get("library/books/${bookId.encodeURLPathPart()}/")
        return json.decodeLibrary<LibraryBookDetailWire>(response.body(), "book detail").toModel()
    }

    override suspend fun list(
        scope: LibraryScope,
        options: BookListOptions
    ): LibraryPage<CompactBook> {
        val parameters = buildList {
            options.q?.let { add("q" to it) }
            options.authorId?.let { add("author" to it) }
            options.seriesId?.let { add("series" to it) }
            options.tagSlug?.let { add("tag" to it) }
            options.ordering?.let { add("ordering" to it.queryValue) }
            add("page" to options.page.toString())
            add("page_size" to options.pageSize.toString())
        }
        return getPage(scope.path("books/"), parameters, options.page, options.pageSize)
    }

    override suspend fun search(
        scope: LibraryScope,
        options: LibrarySearchOptions
    ): LibraryPage<CompactBook> {
        val parameters = buildList {
            add("q" to options.q)
            options.tagSlug?.let { add("tag" to it) }
            options.ordering?.let { add("ordering" to it.queryValue) }
            add("page" to options.page.toString())
            add("page_size" to options.pageSize.toString())
        }
        return getPage(scope.path("search"), parameters, options.page, options.pageSize)
    }

    private suspend fun getPage(
        path: String,
        parameters: List<Pair<String, String>>,
        page: Int,
        pageSize: Int
    ): LibraryPage<CompactBook> {
        val response = requests.get(path, parameters)
        return json.decodeLibrary<CompactBookPageWire>(response.body(), "book page")
            .toModel(page, pageSize)
    }
}

internal fun LibraryScope.path(tail: String): String = when (this) {
    LibraryScope.Global -> "library/$tail"
    is LibraryScope.Group -> "library/groups/${id.encodeURLPathPart()}/$tail"
}
