package com.secondpasslibrary.client

import io.ktor.client.HttpClient
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
    override val marginalia: AuthenticatedMarginaliaClient =
        KtorAuthenticatedMarginaliaClient(requests, json)
}
