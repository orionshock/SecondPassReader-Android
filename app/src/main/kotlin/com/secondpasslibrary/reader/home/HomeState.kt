package com.secondpasslibrary.reader.home

import com.secondpasslibrary.client.ReadingProgress
import com.secondpasslibrary.client.RecentReadingItem
import com.secondpasslibrary.client.ShelfSummary
import com.secondpasslibrary.reader.design.book.BookCardAction

internal data class HomeUiState(
    val showClosedSessions: Boolean = false,
    val recentReading: HomeProjectionState<RecentReadingItem> = HomeProjectionState.loading(),
    val shelves: HomeProjectionState<ShelfSummary> = HomeProjectionState.loading()
)

sealed interface HomeNavigationIntent {
    data class LibrarySearch(val query: String) : HomeNavigationIntent

    data class BookAction(val action: BookCardAction) : HomeNavigationIntent

    data class OpenReadingSessionDetail(
        val sessionId: String,
        val action: ReadingSessionDetailAction = ReadingSessionDetailAction.VIEW
    ) : HomeNavigationIntent

    data object ViewAllSessions : HomeNavigationIntent

    data object OpenShelves : HomeNavigationIntent

    data class OpenShelfDetail(val shelfId: String, val origin: HomeShelfOrigin) :
        HomeNavigationIntent
}

enum class HomeShelfOrigin {
    PERSONAL,
    SHARED,
    GROUP
}

enum class ReadingSessionDetailAction {
    VIEW,
    EDIT,
    CLOSE
}

/**
 * Home retains the Session/progress context for future resume support. The current Reader launch
 * deliberately consumes only [bookId].
 */
internal data class OpenReaderIntent(
    val bookId: String,
    val sessionId: String,
    val progress: ReadingProgress?
) : HomeNavigationIntent

internal sealed interface HomeConnectionEvent {
    data object AuthenticationRejected : HomeConnectionEvent
}
