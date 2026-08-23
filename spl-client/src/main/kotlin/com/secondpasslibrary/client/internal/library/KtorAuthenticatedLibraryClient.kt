package com.secondpasslibrary.client.internal.library

import com.secondpasslibrary.client.AuthenticatedLibraryAuthorsClient
import com.secondpasslibrary.client.AuthenticatedLibraryBooksClient
import com.secondpasslibrary.client.AuthenticatedLibraryClient
import com.secondpasslibrary.client.AuthenticatedLibraryGroupsClient
import com.secondpasslibrary.client.AuthenticatedLibrarySeriesClient
import com.secondpasslibrary.client.AuthenticatedLibraryTagsClient
import com.secondpasslibrary.client.internal.transport.AuthenticatedRequestExecutor
import kotlinx.serialization.json.Json

internal class KtorAuthenticatedLibraryClient(requests: AuthenticatedRequestExecutor, json: Json) :
    AuthenticatedLibraryClient {
    override val books: AuthenticatedLibraryBooksClient = KtorLibraryBooksClient(requests, json)
    override val authors: AuthenticatedLibraryAuthorsClient =
        KtorLibraryAuthorsClient(requests, json)
    override val series: AuthenticatedLibrarySeriesClient = KtorLibrarySeriesClient(requests, json)
    override val groups: AuthenticatedLibraryGroupsClient = KtorLibraryGroupsClient(requests, json)
    override val tags: AuthenticatedLibraryTagsClient = KtorLibraryTagsClient(requests, json)
}
