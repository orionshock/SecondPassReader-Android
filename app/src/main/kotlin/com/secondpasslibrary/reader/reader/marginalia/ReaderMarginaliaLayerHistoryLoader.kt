package com.secondpasslibrary.reader.reader.marginalia

import com.secondpasslibrary.client.BookReadingSessionHistory
import com.secondpasslibrary.client.BookReadingSessionListOptions
import com.secondpasslibrary.client.ReadingSessionStatus as SplReadingSessionStatus
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import com.secondpasslibrary.reader.sessions.history.BookScopedReadingSessionHistoryLoader
import com.secondpasslibrary.reader.sessions.history.BookScopedReadingSessionHistoryResult
import javax.inject.Inject

internal data class ReaderMarginaliaLayerHistoryPage(
    val layers: List<ReaderMarginaliaLayerSummary>,
    val page: Int,
    val hasMore: Boolean
)

internal fun interface ReaderMarginaliaLayerHistoryLoader {
    suspend fun load(
        profile: ConnectionProfile,
        bookId: String,
        page: Int
    ): ReaderMarginaliaLayerHistoryPage
}

internal class SplReaderMarginaliaLayerHistoryLoader @Inject constructor(
    private val clientProvider: AuthenticatedClientProvider,
    private val bookHistoryLoader: BookScopedReadingSessionHistoryLoader
) : ReaderMarginaliaLayerHistoryLoader {
    override suspend fun load(
        profile: ConnectionProfile,
        bookId: String,
        page: Int
    ): ReaderMarginaliaLayerHistoryPage {
        require(bookId.isNotBlank()) { "Book ID must not be blank." }
        require(page > 0) { "History page must be positive." }
        val books = clientProvider.forProfile(profile).marginalia.books
        val options = BookReadingSessionListOptions(page = page)
        if (page > 1) {
            return books.listSessions(bookId, options).toReaderMarginaliaLayerHistoryPage()
        }
        return bookHistoryLoader.loadFirstPage(books, bookId, options)
            .toReaderMarginaliaLayerHistoryPage()
    }
}

internal fun BookScopedReadingSessionHistoryResult.toReaderMarginaliaLayerHistoryPage() =
    when (this) {
        is BookScopedReadingSessionHistoryResult.History ->
            value.toReaderMarginaliaLayerHistoryPage()

        is BookScopedReadingSessionHistoryResult.VisibleBookWithoutHistory ->
            ReaderMarginaliaLayerHistoryPage(emptyList(), page = 1, hasMore = false)

        BookScopedReadingSessionHistoryResult.ProtocolInvalidActiveSessionMissingHistory ->
            throw SplClientException.ProtocolInvalid("Book reading sessions")
    }

private fun BookReadingSessionHistory.toReaderMarginaliaLayerHistoryPage() =
    ReaderMarginaliaLayerHistoryPage(
        layers = sessions.results.map { session ->
            ReaderMarginaliaLayerSummary(
                sessionId = session.id,
                role = ReaderMarginaliaLayerRole.PREVIOUS,
                sessionStatus = when (session.status) {
                    SplReadingSessionStatus.ACTIVE -> ReaderSessionStatus.ACTIVE
                    SplReadingSessionStatus.CLOSED -> ReaderSessionStatus.CLOSED
                },
                sessionName = session.name,
                startedAt = session.startedAt,
                closedAt = session.closedAt,
                lastActivityAt = session.lastActivityAt,
                annotationCount = session.annotationCount
            )
        },
        page = sessions.page,
        hasMore = sessions.hasNext
    )
