package com.secondpasslibrary.client.internal.marginalia

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
import com.secondpasslibrary.client.ReadingSessionStatus
import com.secondpasslibrary.client.RecentReadingItem
import com.secondpasslibrary.client.RecentReadingOptions
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.client.internal.transport.AuthenticatedRequestExecutor
import com.secondpasslibrary.client.internal.transport.requireAuthenticatedSuccess
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.json.Json

internal class KtorAuthenticatedMarginaliaClient(
    requests: AuthenticatedRequestExecutor,
    json: Json
) : AuthenticatedMarginaliaClient {
    private val lifecycle = KtorReadingSessionLifecycleClient(requests, json)
    private val synchronization = KtorMarginaliaSynchronizationClient(requests, json)
    override val books: AuthenticatedMarginaliaBooksClient =
        KtorMarginaliaBooksClient(requests, lifecycle)
    override val sessions: AuthenticatedReadingSessionsClient =
        KtorReadingSessionsClient(requests, lifecycle, synchronization)
}

internal class KtorMarginaliaBooksClient(
    private val requests: AuthenticatedRequestExecutor,
    private val lifecycle: KtorReadingSessionLifecycleClient
) : AuthenticatedMarginaliaBooksClient {
    override suspend fun list(
        options: MarginaliaBookListOptions
    ): MarginaliaPage<MarginaliaBookSummary> {
        val parameters = buildList {
            options.q?.let { add("q" to it) }
            addAll(options.pageParameters())
        }
        return requests.getDecoded<MarginaliaBookPageWire>(
            "marginalia/books/",
            parameters,
            "marginalia books"
        )
            .toModel(options.page, options.pageSize)
    }

    override suspend fun get(bookId: String): MarginaliaBookSummary {
        require(bookId.isNotBlank()) { "Marginalia Book ID must not be blank." }
        return requests.getDecoded<MarginaliaBookWire>(
            "marginalia/books/${bookId.encodeURLPathPart()}/",
            context = "marginalia book"
        )
            .toModel()
    }

    override suspend fun listSessions(
        bookId: String,
        options: BookReadingSessionListOptions
    ): BookReadingSessionHistory {
        require(bookId.isNotBlank()) { "Marginalia Book ID must not be blank." }
        val parameters = buildList {
            options.status?.let { add("status" to it.queryValue) }
            options.q?.let { add("q" to it) }
            addAll(options.pageParameters())
        }
        val response =
            requests.getResponse(
                "marginalia/books/${bookId.encodeURLPathPart()}/sessions/",
                parameters
            )
        if (response.status == HttpStatusCode.NotFound) {
            throw SplClientException.BookReadingSessionHistoryNotFound()
        }
        requireAuthenticatedSuccess(response.status)
        return requests.decode<BookReadingSessionPageWire>(
            response,
            "Book reading sessions"
        ).toModel(options.page, options.pageSize)
    }

    override suspend fun getActiveSession(bookId: String): ReadingSessionBootstrap =
        lifecycle.getActiveSession(bookId)

    override suspend fun openSession(
        bookId: String,
        metadata: ReadingSessionMetadataInput
    ): ReadingSessionBootstrap = lifecycle.openSession(bookId, metadata)

    override suspend fun startOver(
        bookId: String,
        idempotencyKey: MarginaliaIdempotencyKey,
        finalization: ReadingSessionFinalization
    ): ReadingSessionBootstrap = lifecycle.startOver(bookId, idempotencyKey, finalization)
}

internal class KtorReadingSessionsClient(
    private val requests: AuthenticatedRequestExecutor,
    private val lifecycle: KtorReadingSessionLifecycleClient,
    private val synchronization: KtorMarginaliaSynchronizationClient
) : AuthenticatedReadingSessionsClient {
    override suspend fun list(
        options: ReadingSessionListOptions
    ): MarginaliaPage<ReadingSessionListItem> {
        val parameters = buildList {
            options.status?.let { add("status" to it.queryValue) }
            options.q?.let { add("q" to it) }
            options.hasAnnotations?.let { add("has_annotations" to it.toString()) }
            addAll(options.pageParameters())
        }
        return requests.getDecoded<ReadingSessionPageWire>(
            "marginalia/sessions/",
            parameters,
            "reading sessions"
        )
            .toModel(options.page, options.pageSize)
    }

    override suspend fun recent(options: RecentReadingOptions): List<RecentReadingItem> =
        requests.getDecoded<RecentReadingResponseWire>(
            "marginalia/sessions/recent/",
            listOf(
                "limit" to options.limit.toString(),
                "include_closed" to options.includeClosed.toString()
            ),
            "recent reading"
        )
            .toModel()

    override suspend fun get(sessionId: String): ReadingSessionDetailResult {
        require(sessionId.isNotBlank()) { "Reading Session ID must not be blank." }
        return requests.getDecoded<ReadingSessionDetailWire>(
            "marginalia/sessions/${sessionId.encodeURLPathPart()}/",
            context = "reading session detail"
        ).toModel()
    }

    override suspend fun updateMetadata(
        sessionId: String,
        metadata: ReadingSessionMetadataInput
    ): ReadingSessionDetailResult = lifecycle.updateMetadata(sessionId, metadata)

    override suspend fun close(
        sessionId: String,
        finalization: ReadingSessionFinalization
    ): ReadingSessionDetailResult = lifecycle.close(sessionId, finalization)

    override suspend fun getProgress(sessionId: String): ReadingProgress? =
        synchronization.getProgress(sessionId)

    override suspend fun replaceProgress(
        sessionId: String,
        progress: ReadingProgressInput
    ): ReadingProgress = synchronization.replaceProgress(sessionId, progress)

    override suspend fun listAnnotations(sessionId: String): List<MarginaliaAnnotation> =
        synchronization.listAnnotations(sessionId)

    override suspend fun synchronizeAnnotations(
        sessionId: String,
        operations: List<MarginaliaAnnotationOperation>
    ): List<MarginaliaAnnotation> = synchronization.synchronizeAnnotations(sessionId, operations)
}

private val ReadingSessionStatus.queryValue: String
    get() = when (this) {
        ReadingSessionStatus.ACTIVE -> "active"
        ReadingSessionStatus.CLOSED -> "closed"
    }

private fun MarginaliaBookListOptions.pageParameters() =
    listOf("page" to page.toString(), "page_size" to pageSize.toString())

private fun ReadingSessionListOptions.pageParameters() =
    listOf("page" to page.toString(), "page_size" to pageSize.toString())

private fun BookReadingSessionListOptions.pageParameters() =
    listOf("page" to page.toString(), "page_size" to pageSize.toString())
