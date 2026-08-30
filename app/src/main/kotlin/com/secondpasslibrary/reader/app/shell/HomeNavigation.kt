package com.secondpasslibrary.reader.app.shell

import com.secondpasslibrary.reader.home.HomeNavigationIntent
import com.secondpasslibrary.reader.home.HomeShelfOrigin
import com.secondpasslibrary.reader.home.OpenReaderIntent
import com.secondpasslibrary.reader.home.ReadingSessionDetailAction
import com.secondpasslibrary.reader.marginalia.ReadingSessionDetailEntryAction

internal fun AppNavigator.handleHomeNavigation(intent: HomeNavigationIntent) {
    when (intent) {
        is HomeNavigationIntent.LibrarySearch -> openLibrarySearch(intent.query)

        is HomeNavigationIntent.BookAction ->
            handleHomeBookAction(intent.action)

        is HomeNavigationIntent.OpenReadingSessionDetail ->
            openReadingSessionDetail(
                intent.sessionId,
                ReadingSessionDetailReturnTarget.Home,
                intent.action.toRouteAction()
            )

        is OpenReaderIntent ->
            openReader(intent.bookId, ReaderReturnTarget.Home, intent.sessionId, intent.title)

        HomeNavigationIntent.OpenShelves -> select(AppDestination.Shelves)

        is HomeNavigationIntent.OpenShelfDetail ->
            openShelfDetail(intent.shelfId, intent.origin.toRouteOrigin())

        HomeNavigationIntent.ViewAllSessions -> select(AppDestination.Marginalia)
    }
}

private fun HomeShelfOrigin.toRouteOrigin(): ShelfCollectionOrigin = when (this) {
    HomeShelfOrigin.PERSONAL -> ShelfCollectionOrigin.PERSONAL
    HomeShelfOrigin.SHARED -> ShelfCollectionOrigin.SHARED
    HomeShelfOrigin.GROUP -> ShelfCollectionOrigin.GROUP
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
