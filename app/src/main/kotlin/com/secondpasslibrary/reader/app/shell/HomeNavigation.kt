package com.secondpasslibrary.reader.app.shell

import com.secondpasslibrary.reader.home.HomeNavigationIntent
import com.secondpasslibrary.reader.home.ReadingSessionDetailAction
import com.secondpasslibrary.reader.marginalia.ReadingSessionDetailEntryAction

internal fun AppNavigator.handleHomeNavigation(intent: HomeNavigationIntent) {
    when (intent) {
        is HomeNavigationIntent.LibrarySearch -> openLibrarySearch(intent.query)

        is HomeNavigationIntent.OpenBookDetail ->
            openBookDetail(intent.bookId, BookDetailReturnTarget.Home)

        is HomeNavigationIntent.OpenReadingSessionDetail ->
            openReadingSessionDetail(
                intent.sessionId,
                ReadingSessionDetailReturnTarget.Home,
                intent.action.toRouteAction()
            )

        HomeNavigationIntent.OpenShelves -> select(AppDestination.Shelves)

        HomeNavigationIntent.ViewAllSessions -> select(AppDestination.Marginalia)
    }
}

private fun ReadingSessionDetailAction.toRouteAction(): ReadingSessionDetailRouteAction =
    when (this) {
        ReadingSessionDetailAction.VIEW -> ReadingSessionDetailRouteAction.VIEW
        ReadingSessionDetailAction.EDIT -> ReadingSessionDetailRouteAction.EDIT
        ReadingSessionDetailAction.CLOSE -> ReadingSessionDetailRouteAction.CLOSE
    }

internal fun ReadingSessionDetailRouteAction.toMarginaliaEntryAction():
    ReadingSessionDetailEntryAction =
    when (this) {
        ReadingSessionDetailRouteAction.VIEW -> ReadingSessionDetailEntryAction.VIEW
        ReadingSessionDetailRouteAction.EDIT -> ReadingSessionDetailEntryAction.EDIT
        ReadingSessionDetailRouteAction.CLOSE -> ReadingSessionDetailEntryAction.CLOSE
    }
