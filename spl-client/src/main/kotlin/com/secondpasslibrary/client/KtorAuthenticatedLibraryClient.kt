package com.secondpasslibrary.client

import kotlinx.serialization.json.Json

internal class KtorAuthenticatedLibraryClient(requests: AuthenticatedRequestExecutor, json: Json) :
    AuthenticatedLibraryClient {
    override val books: AuthenticatedLibraryBooksClient = KtorLibraryBooksClient(requests, json)
    override val authors: AuthenticatedLibraryAuthorsClient =
        KtorLibraryAuthorsClient(requests, json)
    override val series: AuthenticatedLibrarySeriesClient = KtorLibrarySeriesClient(requests, json)
    override val groups: AuthenticatedLibraryGroupsClient = KtorLibraryGroupsClient(requests, json)
}
