package com.secondpasslibrary.client.internal.library

import com.secondpasslibrary.client.AuthenticatedLibraryAuthorsClient
import com.secondpasslibrary.client.AuthenticatedLibraryBooksClient
import com.secondpasslibrary.client.AuthenticatedLibraryClient
import com.secondpasslibrary.client.AuthenticatedLibraryGroupsClient
import com.secondpasslibrary.client.AuthenticatedLibrarySeriesClient
import com.secondpasslibrary.client.AuthenticatedLibraryTagsClient
import com.secondpasslibrary.client.internal.transport.AuthenticatedRequestExecutor
internal class KtorAuthenticatedLibraryClient(requests: AuthenticatedRequestExecutor) :
    AuthenticatedLibraryClient {
    override val books: AuthenticatedLibraryBooksClient = KtorLibraryBooksClient(requests)
    override val authors: AuthenticatedLibraryAuthorsClient =
        KtorLibraryAuthorsClient(requests)
    override val series: AuthenticatedLibrarySeriesClient = KtorLibrarySeriesClient(requests)
    override val groups: AuthenticatedLibraryGroupsClient = KtorLibraryGroupsClient(requests)
    override val tags: AuthenticatedLibraryTagsClient = KtorLibraryTagsClient(requests)
}
