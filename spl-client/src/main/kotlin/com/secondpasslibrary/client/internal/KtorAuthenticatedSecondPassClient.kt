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
import com.secondpasslibrary.client.internal.transport.splProtocolJson
import io.ktor.client.HttpClient

internal class KtorAuthenticatedSecondPassClient(
    httpClient: HttpClient,
    apiBaseUrl: String,
    credential: BearerCredential,
    onUnreachable: (String) -> Unit = {}
) : AuthenticatedSecondPassClient {
    private val json = splProtocolJson
    private val requests = AuthenticatedRequestExecutor(
        httpClient,
        apiBaseUrl,
        credential,
        json,
        onUnreachable
    )

    override val library: AuthenticatedLibraryClient =
        KtorAuthenticatedLibraryClient(requests)
    override val shelves: AuthenticatedShelvesClient = KtorShelvesClient(requests, json)
    override val marginalia: AuthenticatedMarginaliaClient =
        KtorAuthenticatedMarginaliaClient(requests, json)
}
