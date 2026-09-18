package com.secondpasslibrary.reader.sessions.history

import com.secondpasslibrary.client.AuthenticatedMarginaliaBooksClient
import com.secondpasslibrary.client.BookReadingSessionHistory
import com.secondpasslibrary.client.BookReadingSessionListOptions
import com.secondpasslibrary.client.ReadingSessionBook
import com.secondpasslibrary.client.SplClientException
import javax.inject.Inject

internal class BookScopedReadingSessionHistoryLoader @Inject constructor() {
    suspend fun loadFirstPage(
        books: AuthenticatedMarginaliaBooksClient,
        bookId: String,
        options: BookReadingSessionListOptions
    ): BookScopedReadingSessionHistoryResult {
        require(options.page == 1) { "Book-scoped initial history must request page one." }
        return try {
            BookScopedReadingSessionHistoryResult.History(
                books.listSessions(bookId, options)
            )
        } catch (_: SplClientException.BookReadingSessionHistoryNotFound) {
            val bootstrap = books.getActiveSession(bookId)
            if (bootstrap.activeSession != null) {
                BookScopedReadingSessionHistoryResult.ProtocolInvalidActiveSessionMissingHistory
            } else {
                BookScopedReadingSessionHistoryResult.VisibleBookWithoutHistory(bootstrap.book)
            }
        }
    }
}

internal sealed interface BookScopedReadingSessionHistoryResult {
    data class History(val value: BookReadingSessionHistory) :
        BookScopedReadingSessionHistoryResult

    data class VisibleBookWithoutHistory(val book: ReadingSessionBook) :
        BookScopedReadingSessionHistoryResult

    data object ProtocolInvalidActiveSessionMissingHistory :
        BookScopedReadingSessionHistoryResult
}
