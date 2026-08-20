package com.secondpasslibrary.client

import com.secondpasslibrary.client.internal.BookReadingSessionPageWire
import com.secondpasslibrary.client.internal.MarginaliaBookPageWire
import com.secondpasslibrary.client.internal.MarginaliaBookWire
import com.secondpasslibrary.client.internal.ReadingSessionDetailWire
import com.secondpasslibrary.client.internal.ReadingSessionPageWire
import com.secondpasslibrary.client.internal.RecentReadingResponseWire
import io.ktor.client.call.body
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.json.Json

internal class KtorAuthenticatedMarginaliaClient(
    requests: AuthenticatedRequestExecutor,
    json: Json
) : AuthenticatedMarginaliaClient {
    override val books: AuthenticatedMarginaliaBooksClient =
        KtorMarginaliaBooksClient(requests, json)
    override val sessions: AuthenticatedReadingSessionsClient =
        KtorReadingSessionsClient(requests, json)
}

internal class KtorMarginaliaBooksClient(
    private val requests: AuthenticatedRequestExecutor,
    private val json: Json
) : AuthenticatedMarginaliaBooksClient {
    override suspend fun list(
        options: MarginaliaBookListOptions
    ): MarginaliaPage<MarginaliaBookSummary> {
        val response = requests.get("marginalia/books/", options.pageParameters())
        return json.decodeLibrary<MarginaliaBookPageWire>(response.body(), "marginalia books")
            .toModel(options.page, options.pageSize)
    }

    override suspend fun get(bookId: String): MarginaliaBookSummary {
        require(bookId.isNotBlank()) { "Marginalia Book ID must not be blank." }
        val response = requests.get("marginalia/books/${bookId.encodeURLPathPart()}/")
        return json.decodeLibrary<MarginaliaBookWire>(response.body(), "marginalia book")
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
            requests.get(
                "marginalia/books/${bookId.encodeURLPathPart()}/sessions/",
                parameters
            )
        return json.decodeLibrary<BookReadingSessionPageWire>(
            response.body(),
            "Book reading sessions"
        ).toModel(options.page, options.pageSize)
    }
}

internal class KtorReadingSessionsClient(
    private val requests: AuthenticatedRequestExecutor,
    private val json: Json
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
        val response = requests.get("marginalia/sessions/", parameters)
        return json.decodeLibrary<ReadingSessionPageWire>(response.body(), "reading sessions")
            .toModel(options.page, options.pageSize)
    }

    override suspend fun recent(options: RecentReadingOptions): List<RecentReadingItem> {
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

    override suspend fun get(sessionId: String): ReadingSessionDetailResult {
        require(sessionId.isNotBlank()) { "Reading Session ID must not be blank." }
        val response = requests.get("marginalia/sessions/${sessionId.encodeURLPathPart()}/")
        return json.decodeLibrary<ReadingSessionDetailWire>(
            response.body(),
            "reading session detail"
        ).toModel()
    }
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
