package com.secondpasslibrary.client

import com.secondpasslibrary.client.internal.CompactBookPageWire
import io.ktor.client.call.body
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.json.Json

internal class KtorLibraryBooksClient(
    private val requests: AuthenticatedRequestExecutor,
    private val json: Json
) : AuthenticatedLibraryBooksClient {
    override suspend fun listBooks(options: BookListOptions): LibraryPage<CompactBook> {
        val parameters = buildList {
            options.q?.let { add("q" to it) }
            options.authorId?.let { add("author" to it) }
            options.seriesId?.let { add("series" to it) }
            options.tagSlug?.let { add("tag" to it) }
            options.ordering?.let { add("ordering" to it.queryValue) }
            add("page" to options.page.toString())
            add("page_size" to options.pageSize.toString())
        }
        return getPage("library/books/", parameters, options.page, options.pageSize)
    }

    override suspend fun searchLibrary(options: LibrarySearchOptions): LibraryPage<CompactBook> {
        val parameters = buildList {
            add("q" to options.q)
            options.ordering?.let { add("ordering" to it.queryValue) }
            add("page" to options.page.toString())
            add("page_size" to options.pageSize.toString())
        }
        return getPage("library/search", parameters, options.page, options.pageSize)
    }

    override suspend fun listGroupBooks(
        groupId: String,
        options: GroupBookListOptions
    ): LibraryPage<CompactBook> {
        require(groupId.isNotBlank()) { "Library group ID must not be blank." }
        val parameters = buildList {
            options.q?.let { add("q" to it) }
            options.ordering?.let { add("ordering" to it.queryValue) }
            add("page" to options.page.toString())
            add("page_size" to options.pageSize.toString())
        }
        return getPage(
            "library/groups/${groupId.encodeURLPathPart()}/books/",
            parameters,
            options.page,
            options.pageSize
        )
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
