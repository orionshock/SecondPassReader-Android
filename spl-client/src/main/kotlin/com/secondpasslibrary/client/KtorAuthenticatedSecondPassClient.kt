package com.secondpasslibrary.client

import com.secondpasslibrary.client.internal.CompactBookPageWire
import com.secondpasslibrary.client.internal.RecentReadingResponseWire
import com.secondpasslibrary.client.internal.ShelfPageWire
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import java.io.IOException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
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
