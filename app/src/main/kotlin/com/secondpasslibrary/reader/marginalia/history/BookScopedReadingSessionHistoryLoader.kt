package com.secondpasslibrary.reader.marginalia.history

import com.secondpasslibrary.client.AuthenticatedMarginaliaBooksClient
import com.secondpasslibrary.client.BookReadingSessionHistory
import com.secondpasslibrary.client.BookReadingSessionListOptions
import com.secondpasslibrary.client.ReadingSessionBook
import com.secondpasslibrary.client.SplClientException

internal class BookScopedReadingSessionHistoryLoader {
    suspend fun loadInitial(
        books: AuthenticatedMarginaliaBooksClient,
        bookId: String,
        options: BookReadingSessionListOptions
    ): BookScopedReadingSessionHistoryLoadResult {
        require(options.page == 1) { "Book-scoped initial history must request page one." }
        return try {
            BookScopedReadingSessionHistoryLoadResult.LinkedHistory(
                books.listSessions(bookId, options)
            )
        } catch (_: SplClientException.BookReadingSessionHistoryNotFound) {
            val bootstrap = books.getActiveSession(bookId)
            if (bootstrap.activeSession != null) {
                throw SplClientException.ProtocolInvalid("Book reading sessions")
            }
            BookScopedReadingSessionHistoryLoadResult.VisibleBookWithoutHistory(bootstrap.book)
        }
    }
}

internal sealed interface BookScopedReadingSessionHistoryLoadResult {
    data class LinkedHistory(val history: BookReadingSessionHistory) :
        BookScopedReadingSessionHistoryLoadResult

    data class VisibleBookWithoutHistory(val book: ReadingSessionBook) :
        BookScopedReadingSessionHistoryLoadResult
}
