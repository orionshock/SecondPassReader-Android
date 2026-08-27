package com.secondpasslibrary.reader.reader.marginalia

import com.secondpasslibrary.client.BookReadingSessionListOptions
import com.secondpasslibrary.client.ReadingSessionStatus as SplReadingSessionStatus
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
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
    private val clientProvider: AuthenticatedClientProvider
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
        val history = try {
            books.listSessions(bookId, options)
        } catch (notFound: SplClientException.BookReadingSessionHistoryNotFound) {
            if (page != 1) throw notFound
            val bootstrap = books.getActiveSession(bookId)
            if (bootstrap.activeSession != null) {
                throw SplClientException.ProtocolInvalid("Book reading sessions")
            }
            return ReaderMarginaliaLayerHistoryPage(emptyList(), page = 1, hasMore = false)
        }
        return ReaderMarginaliaLayerHistoryPage(
            layers = history.sessions.results.map { session ->
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
            page = history.sessions.page,
            hasMore = history.sessions.hasNext
        )
    }
}
