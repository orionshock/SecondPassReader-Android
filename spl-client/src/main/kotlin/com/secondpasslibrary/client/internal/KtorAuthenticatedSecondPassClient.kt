package com.secondpasslibrary.client.internal

import com.secondpasslibrary.client.AuthenticatedLibraryClient
import com.secondpasslibrary.client.AuthenticatedMarginaliaClient
import com.secondpasslibrary.client.AuthenticatedSecondPassClient
import com.secondpasslibrary.client.AuthenticatedShelvesClient
import com.secondpasslibrary.client.BearerCredential
import com.secondpasslibrary.client.internal.library.KtorAuthenticatedLibraryClient
import com.secondpasslibrary.client.internal.marginalia.KtorAuthenticatedMarginaliaClient
import com.secondpasslibrary.client.internal.shelves.KtorShelvesClient
import com.secondpasslibrary.client.internal.transport.AuthenticatedRequestExecutor
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
