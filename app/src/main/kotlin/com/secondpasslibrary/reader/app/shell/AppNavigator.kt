package com.secondpasslibrary.reader.app.shell

import com.secondpasslibrary.reader.bookdetail.BookDetailNavigationIntent
import com.secondpasslibrary.reader.marginalia.MarginaliaExternalNavigationIntent

@Suppress("TooManyFunctions") // Typed shell navigation commands remain explicit by destination.
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

    fun openReader(
        bookId: String,
        returnTarget: ReaderReturnTarget,
        existingSessionId: String? = null,
        titleHint: String? = null
    ) {
        require(bookId.isNotBlank()) { "Book ID must not be blank." }
        navigation.push(ReaderRoute(bookId, returnTarget, existingSessionId, titleHint))
    }

    fun openBookMarginalia(bookId: String, source: BookDetailRoute) {
        require(bookId.isNotBlank()) { "Book ID must not be blank." }
        require(source.bookId == bookId) { "Marginalia Book must match its Book Detail source." }
        val route = BookMarginaliaRoute(bookId, MarginaliaReturnTarget.BookDetail(source))
        navigation.push(route)
    }

    fun openLibraryBookMarginalia(bookId: String) {
        require(bookId.isNotBlank()) { "Book ID must not be blank." }
        navigation.push(BookMarginaliaRoute(bookId, MarginaliaReturnTarget.Library))
    }

    fun openLibraryAuthor(authorId: String) =
        navigation.replace(AppDestination.Library, LibraryAuthorRoute(authorId))

    fun openLibrarySeries(seriesId: String) =
        navigation.replace(AppDestination.Library, LibrarySeriesRoute(seriesId))

    fun openLibraryTag(tagId: String, tagSlug: String) =
        navigation.replace(AppDestination.Library, LibraryTagRoute(tagId, tagSlug))

    fun openShelfDetail(shelfId: String, origin: ShelfCollectionOrigin) {
        require(shelfId.isNotBlank()) { "Shelf ID must not be blank." }
        navigation.select(AppDestination.Shelves)
        navigation.push(ShelfDetailRoute(shelfId, origin))
    }

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

            is BookDetailNavigationIntent.ReadBook -> {
                require(intent.bookId == source.bookId) {
                    "Reader Book must match its Book Detail source."
                }
                openReader(
                    intent.bookId,
                    ReaderReturnTarget.BookDetail(source),
                    titleHint = intent.title
                )
            }

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

    fun handleTopLevelMarginaliaNavigation(intent: MarginaliaExternalNavigationIntent) {
        handleMarginaliaNavigation(
            intent,
            BookDetailReturnTarget.Marginalia,
            ReaderReturnTarget.Marginalia
        )
    }

    fun handleBookMarginaliaNavigation(
        intent: MarginaliaExternalNavigationIntent,
        source: BookMarginaliaRoute
    ) {
        handleMarginaliaNavigation(
            intent,
            BookDetailReturnTarget.BookMarginalia(source),
            ReaderReturnTarget.BookMarginalia(source)
        )
    }

    fun handleReadingSessionDetailNavigation(
        intent: MarginaliaExternalNavigationIntent,
        source: ReadingSessionDetailRoute
    ) {
        handleMarginaliaNavigation(
            intent,
            BookDetailReturnTarget.ReadingSessionDetail(source),
            ReaderReturnTarget.ReadingSessionDetail(source)
        )
    }

    private fun handleMarginaliaNavigation(
        intent: MarginaliaExternalNavigationIntent,
        bookDetailReturnTarget: BookDetailReturnTarget,
        readerReturnTarget: ReaderReturnTarget
    ) {
        when (intent) {
            is MarginaliaExternalNavigationIntent.BookDetail ->
                openBookDetail(intent.bookId, bookDetailReturnTarget)

            is MarginaliaExternalNavigationIntent.Reader ->
                openReader(intent.bookId, readerReturnTarget, intent.sessionId)
        }
    }

    fun goBack(): Boolean = navigation.pop()
}
