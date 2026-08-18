package com.secondpasslibrary.client

import com.secondpasslibrary.client.internal.RecentReadingResponseWire
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import kotlinx.serialization.ExperimentalSerializationApi
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

    override val library: AuthenticatedLibraryClient =
        KtorAuthenticatedLibraryClient(requests, json)
    override val shelves: AuthenticatedShelvesClient = KtorShelvesClient(requests, json)

    override suspend fun recentReading(options: RecentReadingOptions): List<RecentReadingItem> {
        val response =
            requests.get(
                "marginalia/sessions/recent/",
                listOf(
                    "limit" to options.limit.toString(),
                    "include_closed" to options.includeClosed.toString()
                )
            )
        return json.decodeLibrary<RecentReadingResponseWire>(response.body(), "recent reading")
            .toModel()
    }
}
