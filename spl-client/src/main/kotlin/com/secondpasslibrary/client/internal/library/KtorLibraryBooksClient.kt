package com.secondpasslibrary.client.internal.library

import com.secondpasslibrary.client.AuthenticatedBookDownloadReference
import com.secondpasslibrary.client.AuthenticatedLibraryBooksClient
import com.secondpasslibrary.client.BookListOptions
import com.secondpasslibrary.client.CatalogResultPage
import com.secondpasslibrary.client.CompactBook
import com.secondpasslibrary.client.LibraryBookDetail
import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.client.LibrarySearchOptions
import com.secondpasslibrary.client.internal.transport.AuthenticatedRequestExecutor
import com.secondpasslibrary.client.internal.transport.decodeProtocolBody
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.encodeURLPathPart
import io.ktor.utils.io.jvm.javaio.toInputStream
import java.io.IOException
import java.io.OutputStream
import kotlinx.serialization.json.Json

internal class KtorLibraryBooksClient(
    private val requests: AuthenticatedRequestExecutor,
    private val json: Json
) : AuthenticatedLibraryBooksClient {
    override suspend fun getBook(bookId: String): LibraryBookDetail {
        require(bookId.isNotBlank()) { "Book ID must not be blank." }
        val response = requests.get("library/books/${bookId.encodeURLPathPart()}/")
        return json.decodeProtocolBody<LibraryBookDetailWire>(
            response.body(),
            "book detail"
        ).toModel()
    }

    override suspend fun downloadBook(
        reference: AuthenticatedBookDownloadReference,
        destination: OutputStream
    ) {
        val response = requests.getAuthorizedReference(reference.url)
        com.secondpasslibrary.client.internal.transport.requireAuthenticatedSuccess(response)
        try {
            response.bodyAsChannel().toInputStream().use { input -> input.copyTo(destination) }
        } catch (failure: IOException) {
            throw com.secondpasslibrary.client.SplClientException.ServerUnreachable(failure)
        }
    }

    override suspend fun list(
        scope: LibraryScope,
        options: BookListOptions
    ): CatalogResultPage<CompactBook> {
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
    ): CatalogResultPage<CompactBook> {
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
    ): CatalogResultPage<CompactBook> {
        val response = requests.get(path, parameters)
        return json.decodeProtocolBody<CompactBookPageWire>(response.body(), "book page")
            .toModel(page, pageSize)
    }
}

internal fun LibraryScope.path(tail: String): String = when (this) {
    LibraryScope.Global -> "library/$tail"
    is LibraryScope.Group -> "library/groups/${id.encodeURLPathPart()}/$tail"
}
