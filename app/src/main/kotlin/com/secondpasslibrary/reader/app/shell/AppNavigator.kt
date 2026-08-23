package com.secondpasslibrary.reader.app.shell

import com.secondpasslibrary.reader.bookdetail.BookDetailNavigationIntent

internal class AppNavigator(private val navigation: AppNavigationState) {
    fun select(destination: AppDestination) {
        navigation.select(destination)
    }

    fun openLibrarySearch(query: String) {
        navigation.replace(AppDestination.Library, LibrarySearchRoute(query))
    }

    fun openBookDetail(bookId: String, returnTarget: BookDetailReturnTarget) {
        require(bookId.isNotBlank()) { "Book ID must not be blank." }
        val route = BookDetailRoute(bookId, returnTarget)
        navigation.push(route)
    }

    fun openBookMarginalia(bookId: String, source: BookDetailRoute) {
        require(bookId.isNotBlank()) { "Book ID must not be blank." }
        require(source.bookId == bookId) { "Marginalia Book must match its Book Detail source." }
        val route = BookMarginaliaRoute(bookId, MarginaliaReturnTarget.BookDetail(source))
        navigation.push(route)
    }

    fun openLibraryAuthor(authorId: String) =
        navigation.replace(AppDestination.Library, LibraryAuthorRoute(authorId))

    fun openLibrarySeries(seriesId: String) =
        navigation.replace(AppDestination.Library, LibrarySeriesRoute(seriesId))

    fun openLibraryTag(tagId: String, tagSlug: String) =
        navigation.replace(AppDestination.Library, LibraryTagRoute(tagId, tagSlug))

    fun handleBookDetailNavigation(intent: BookDetailNavigationIntent, source: BookDetailRoute) {
        when (intent) {
            is BookDetailNavigationIntent.Author -> {
                navigation.removeTop(source)
                openLibraryAuthor(intent.id)
            }

            is BookDetailNavigationIntent.Series -> {
                navigation.removeTop(source)
                openLibrarySeries(intent.id)
            }

            is BookDetailNavigationIntent.Tag -> {
                navigation.removeTop(source)
                openLibraryTag(intent.id, intent.slug)
            }

            is BookDetailNavigationIntent.ReadingSessions ->
                openBookMarginalia(intent.bookId, source)

            BookDetailNavigationIntent.ManageShelves -> {
                navigation.removeTop(source)
                navigation.replace(AppDestination.Shelves)
            }
        }
    }

    fun openReadingSessionDetail(
        sessionId: String,
        returnTarget: ReadingSessionDetailReturnTarget,
        action: ReadingSessionDetailRouteAction = ReadingSessionDetailRouteAction.VIEW
    ) {
        require(sessionId.isNotBlank()) { "Reading Session ID must not be blank." }
        navigation.push(ReadingSessionDetailRoute(sessionId, returnTarget, action))
    }

    fun goBack(): Boolean = navigation.pop()
}
