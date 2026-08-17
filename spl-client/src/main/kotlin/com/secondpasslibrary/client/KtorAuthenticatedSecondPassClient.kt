package com.secondpasslibrary.client

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
