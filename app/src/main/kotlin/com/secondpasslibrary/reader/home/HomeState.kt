package com.secondpasslibrary.reader.home

import com.secondpasslibrary.client.RecentReadingItem
import com.secondpasslibrary.client.ShelfSummary

data class HomeUiState(
    val showClosedSessions: Boolean = false,
    val recentReading: HomeSectionState<RecentReadingItem> = HomeSectionState.Loading,
    val shelves: HomeSectionState<ShelfSummary> = HomeSectionState.Loading
)

sealed interface HomeSectionState<out T> {
    data object Loading : HomeSectionState<Nothing>

    data class Loaded<T>(val items: List<T>) : HomeSectionState<T>

    data object Empty : HomeSectionState<Nothing>

    data class Error(val message: String) : HomeSectionState<Nothing>
}

sealed interface HomeNavigationIntent {
    data class LibrarySearch(val query: String) : HomeNavigationIntent

    data object ViewAllSessions : HomeNavigationIntent

    data object OpenShelves : HomeNavigationIntent
}
