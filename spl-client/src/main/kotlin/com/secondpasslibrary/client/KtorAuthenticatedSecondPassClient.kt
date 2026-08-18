package com.secondpasslibrary.client

import com.secondpasslibrary.client.internal.CompactBookPageWire
import com.secondpasslibrary.client.internal.LibraryAuthorPageWire
import com.secondpasslibrary.client.internal.LibraryAuthorWire
import com.secondpasslibrary.client.internal.LibraryGroupPageWire
import com.secondpasslibrary.client.internal.LibrarySeriesPageWire
import com.secondpasslibrary.client.internal.LibrarySeriesWire
import com.secondpasslibrary.client.internal.RecentReadingResponseWire
import com.secondpasslibrary.client.internal.ShelfPageWire
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.http.encodeURLPathPart
import java.io.IOException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
@Suppress("TooManyFunctions") // Public SDK operations remain on the authenticated client boundary.
internal class KtorAuthenticatedSecondPassClient(
    httpClient: HttpClient,
    apiBaseUrl: String,
    credential: BearerCredential
) : AuthenticatedSecondPassClient {
    private val requests = AuthenticatedRequestExecutor(httpClient, apiBaseUrl, credential)
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            exceptionsWithDebugInfo = false
        }

    override suspend fun recentReading(options: RecentReadingOptions): List<RecentReadingItem> {
        val response =
            requests.get(
                "marginalia/sessions/recent/",
                listOf(
                    "limit" to options.limit.toString(),
                    "include_closed" to options.includeClosed.toString()
                )
            )
        return decode<RecentReadingResponseWire>(response.body(), "recent reading").toModel()
    }

    override suspend fun listShelves(options: ShelfListOptions): ShelfPage {
        val parameters = buildList {
            options.page?.let { add("page" to it.toString()) }
            options.pageSize?.let { add("page_size" to it.toString()) }
            options.ordering?.let { add("ordering" to it.queryValue) }
            options.previewLimit?.let {
                add("include_preview_books" to "true")
                add("preview_limit" to it.toString())
            }
        }
        val response = requests.get("shelves/", parameters)
        return decode<ShelfPageWire>(response.body(), "shelf list").toModel()
    }

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
        val response = requests.get("library/books/", parameters)
        return decode<CompactBookPageWire>(response.body(), "book page")
            .toModel(options.page, options.pageSize)
    }

    override suspend fun searchLibrary(options: LibrarySearchOptions): LibraryPage<CompactBook> {
        val parameters = buildList {
            add("q" to options.q)
            options.ordering?.let { add("ordering" to it.queryValue) }
            add("page" to options.page.toString())
            add("page_size" to options.pageSize.toString())
        }
        val response = requests.get("library/search", parameters)
        return decode<CompactBookPageWire>(response.body(), "book page")
            .toModel(options.page, options.pageSize)
    }

    override suspend fun listLibraryGroups(
        options: LibraryGroupListOptions
    ): LibraryPage<LibraryGroupSummary> {
        val parameters =
            listOf(
                "ordering" to options.ordering.queryValue,
                "page" to options.page.toString(),
                "page_size" to options.pageSize.toString()
            )
        val response = requests.get("library/groups/", parameters)
        return decode<LibraryGroupPageWire>(response.body(), "library group page")
            .toModel(options.page, options.pageSize)
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
        val response =
            requests.get(
                "library/groups/${groupId.encodeURLPathPart()}/books/",
                parameters
            )
        return decode<CompactBookPageWire>(response.body(), "book page")
            .toModel(options.page, options.pageSize)
    }

    override suspend fun listAuthors(options: AuthorListOptions): LibraryPage<LibraryAuthor> =
        listAuthorsAt("library/authors/", options)

    override suspend fun getAuthor(
        authorId: String,
        options: LibraryEntityDetailOptions
    ): LibraryAuthor {
        require(authorId.isNotBlank()) { "Author ID must not be blank." }
        val response = requests.get(
            "library/authors/${authorId.encodeURLPathPart()}/",
            previewParameters(options.previewLimit)
        )
        return decode<LibraryAuthorWire>(response.body(), "library author").toModel()
    }

    override suspend fun listGroupAuthors(
        groupId: String,
        options: AuthorListOptions
    ): LibraryPage<LibraryAuthor> {
        require(groupId.isNotBlank()) { "Library group ID must not be blank." }
        return listAuthorsAt(
            "library/groups/${groupId.encodeURLPathPart()}/authors/",
            options
        )
    }

    override suspend fun listSeries(options: SeriesListOptions): LibraryPage<LibrarySeries> =
        listSeriesAt("library/series/", options)

    override suspend fun getSeries(
        seriesId: String,
        options: LibraryEntityDetailOptions
    ): LibrarySeries {
        require(seriesId.isNotBlank()) { "Series ID must not be blank." }
        val response = requests.get(
            "library/series/${seriesId.encodeURLPathPart()}/",
            previewParameters(options.previewLimit)
        )
        return decode<LibrarySeriesWire>(response.body(), "library series").toModel()
    }

    override suspend fun listGroupSeries(
        groupId: String,
        options: SeriesListOptions
    ): LibraryPage<LibrarySeries> {
        require(groupId.isNotBlank()) { "Library group ID must not be blank." }
        return listSeriesAt(
            "library/groups/${groupId.encodeURLPathPart()}/series/",
            options
        )
    }

    private suspend fun listAuthorsAt(
        path: String,
        options: AuthorListOptions
    ): LibraryPage<LibraryAuthor> {
        val response = requests.get(path, authorParameters(options))
        return decode<LibraryAuthorPageWire>(response.body(), "library author page")
            .toModel(options.page, options.pageSize)
    }

    private suspend fun listSeriesAt(
        path: String,
        options: SeriesListOptions
    ): LibraryPage<LibrarySeries> {
        val response = requests.get(path, seriesParameters(options))
        return decode<LibrarySeriesPageWire>(response.body(), "library series page")
            .toModel(options.page, options.pageSize)
    }

    private inline fun <reified T> decode(body: String, context: String): T = try {
        json.decodeFromString<T>(body)
    } catch (_: SerializationException) {
        invalidProtocol(context)
    } catch (_: IllegalArgumentException) {
        invalidProtocol(context)
    } catch (_: IOException) {
        invalidProtocol(context)
    }
}

private fun authorParameters(options: AuthorListOptions): List<Pair<String, String>> = buildList {
    options.q?.let { add("q" to it) }
    add("ordering" to options.ordering.queryValue)
    add("page" to options.page.toString())
    add("page_size" to options.pageSize.toString())
    addAll(previewParameters(options.previewLimit))
}

private fun seriesParameters(options: SeriesListOptions): List<Pair<String, String>> = buildList {
    options.q?.let { add("q" to it) }
    add("ordering" to options.ordering.queryValue)
    add("page" to options.page.toString())
    add("page_size" to options.pageSize.toString())
    addAll(previewParameters(options.previewLimit))
}

private fun previewParameters(previewLimit: Int): List<Pair<String, String>> =
    if (previewLimit > 0) {
        listOf(
            "include_preview_books" to "true",
            "preview_limit" to previewLimit.toString()
        )
    } else {
        emptyList()
    }
