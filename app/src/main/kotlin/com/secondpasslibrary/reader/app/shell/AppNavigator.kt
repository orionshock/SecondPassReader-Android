package com.secondpasslibrary.reader.app.shell

import androidx.navigation3.runtime.NavKey
import com.secondpasslibrary.reader.bookdetail.BookDetailNavigationIntent

internal class AppNavigator(private val backStack: MutableList<NavKey>) {
    fun select(destination: AppDestination) {
        if (backStack.lastOrNull() == destination) return
        backStack.clear()
        backStack.add(destination)
    }

    fun openLibrarySearch(query: String) {
        backStack.clear()
        backStack.add(LibrarySearchRoute(query))
    }

    fun openBookDetail(bookId: String, returnTarget: BookDetailReturnTarget) {
        require(bookId.isNotBlank()) { "Book ID must not be blank." }
        val route = BookDetailRoute(bookId, returnTarget)
        if (backStack.lastOrNull() != route) backStack.add(route)
    }

    fun openLibraryAuthor(authorId: String) = replaceWith(LibraryAuthorRoute(authorId))

    fun openLibrarySeries(seriesId: String) = replaceWith(LibrarySeriesRoute(seriesId))

    fun openLibraryTag(tagId: String, tagSlug: String) =
        replaceWith(LibraryTagRoute(tagId, tagSlug))

    fun handleBookDetailNavigation(intent: BookDetailNavigationIntent) {
        when (intent) {
            is BookDetailNavigationIntent.Author -> openLibraryAuthor(intent.id)
            is BookDetailNavigationIntent.Series -> openLibrarySeries(intent.id)
            is BookDetailNavigationIntent.Tag -> openLibraryTag(intent.id, intent.slug)
            BookDetailNavigationIntent.ManageShelves -> select(AppDestination.Shelves)
        }
    }

    fun goBack() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    private fun replaceWith(route: NavKey) {
        backStack.clear()
        backStack.add(route)
    }
}
