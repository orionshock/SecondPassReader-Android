package com.secondpasslibrary.reader

import com.secondpasslibrary.client.AuthenticatedMarginaliaBooksClient
import com.secondpasslibrary.client.AuthenticatedMarginaliaClient
import com.secondpasslibrary.client.AuthenticatedReadingSessionsClient
import com.secondpasslibrary.client.BookReadingSessionHistory
import com.secondpasslibrary.client.BookReadingSessionListOptions
import com.secondpasslibrary.client.MarginaliaAnnotation
import com.secondpasslibrary.client.MarginaliaAnnotationOperation
import com.secondpasslibrary.client.MarginaliaBookListOptions
import com.secondpasslibrary.client.MarginaliaBookSummary
import com.secondpasslibrary.client.MarginaliaIdempotencyKey
import com.secondpasslibrary.client.MarginaliaPage
import com.secondpasslibrary.client.ReadingProgress
import com.secondpasslibrary.client.ReadingProgressInput
import com.secondpasslibrary.client.ReadingSessionBootstrap
import com.secondpasslibrary.client.ReadingSessionDetailResult
import com.secondpasslibrary.client.ReadingSessionFinalization
import com.secondpasslibrary.client.ReadingSessionListItem
import com.secondpasslibrary.client.ReadingSessionListOptions
import com.secondpasslibrary.client.ReadingSessionMetadataInput
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

        override suspend fun getActiveSession(bookId: String): ReadingSessionBootstrap =
            unsupported()

        override suspend fun openSession(
            bookId: String,
            metadata: ReadingSessionMetadataInput
        ): ReadingSessionBootstrap = unsupported()

        override suspend fun startOver(
            bookId: String,
            idempotencyKey: MarginaliaIdempotencyKey,
            finalization: ReadingSessionFinalization
        ): ReadingSessionBootstrap = unsupported()
    }

    override val sessions = object : AuthenticatedReadingSessionsClient {
        override suspend fun list(
            options: ReadingSessionListOptions
        ): MarginaliaPage<ReadingSessionListItem> = unsupported()

        override suspend fun recent(options: RecentReadingOptions): List<RecentReadingItem> =
            unsupported()

        override suspend fun get(sessionId: String): ReadingSessionDetailResult = unsupported()

        override suspend fun updateMetadata(
            sessionId: String,
            metadata: ReadingSessionMetadataInput
        ): ReadingSessionDetailResult = unsupported()

        override suspend fun close(
            sessionId: String,
            finalization: ReadingSessionFinalization
        ): ReadingSessionDetailResult = unsupported()

        override suspend fun getProgress(sessionId: String): ReadingProgress? = unsupported()

        override suspend fun replaceProgress(
            sessionId: String,
            progress: ReadingProgressInput
        ): ReadingProgress = unsupported()

        override suspend fun listAnnotations(sessionId: String): List<MarginaliaAnnotation> =
            unsupported()

        override suspend fun synchronizeAnnotations(
            sessionId: String,
            operations: List<MarginaliaAnnotationOperation>
        ): List<MarginaliaAnnotation> = unsupported()
    }

    private fun unsupported(): Nothing = error("Marginalia is outside this test fixture.")
}
