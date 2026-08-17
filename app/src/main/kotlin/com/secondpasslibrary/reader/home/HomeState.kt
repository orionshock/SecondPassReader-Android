package com.secondpasslibrary.reader.home

import com.secondpasslibrary.client.RecentReadingItem
import com.secondpasslibrary.client.ShelfSummary

internal data class HomeUiState(
    val showClosedSessions: Boolean = false,
    val recentReading: HomeProjectionState<RecentReadingItem> = HomeProjectionState.loading(),
    val shelves: HomeProjectionState<ShelfSummary> = HomeProjectionState.loading()
)

sealed interface HomeNavigationIntent {
    data class LibrarySearch(val query: String) : HomeNavigationIntent

    data object ViewAllSessions : HomeNavigationIntent

    data object OpenShelves : HomeNavigationIntent
}

internal sealed interface HomeConnectionEvent {
    data object AuthenticationRejected : HomeConnectionEvent
}
