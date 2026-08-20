package com.secondpasslibrary.reader

import com.secondpasslibrary.client.AuthenticatedMarginaliaBooksClient
import com.secondpasslibrary.client.AuthenticatedMarginaliaClient
import com.secondpasslibrary.client.AuthenticatedReadingSessionsClient
import com.secondpasslibrary.client.BookReadingSessionHistory
import com.secondpasslibrary.client.BookReadingSessionListOptions
import com.secondpasslibrary.client.MarginaliaBookListOptions
import com.secondpasslibrary.client.MarginaliaBookSummary
import com.secondpasslibrary.client.MarginaliaPage
import com.secondpasslibrary.client.ReadingSessionDetailResult
import com.secondpasslibrary.client.ReadingSessionListItem
import com.secondpasslibrary.client.ReadingSessionListOptions
import com.secondpasslibrary.client.RecentReadingItem
import com.secondpasslibrary.client.RecentReadingOptions

internal object FakeAuthenticatedMarginaliaClient : AuthenticatedMarginaliaClient {
    override val books = object : AuthenticatedMarginaliaBooksClient {
        override suspend fun list(
            options: MarginaliaBookListOptions
        ): MarginaliaPage<MarginaliaBookSummary> = unsupported()

        override suspend fun get(bookId: String): MarginaliaBookSummary = unsupported()

        override suspend fun listSessions(
            bookId: String,
            options: BookReadingSessionListOptions
        ): BookReadingSessionHistory = unsupported()
    }

    override val sessions = object : AuthenticatedReadingSessionsClient {
        override suspend fun list(
            options: ReadingSessionListOptions
        ): MarginaliaPage<ReadingSessionListItem> = unsupported()

        override suspend fun recent(options: RecentReadingOptions): List<RecentReadingItem> =
            unsupported()

        override suspend fun get(sessionId: String): ReadingSessionDetailResult = unsupported()
    }

    private fun unsupported(): Nothing = error("Marginalia is outside this test fixture.")
}
