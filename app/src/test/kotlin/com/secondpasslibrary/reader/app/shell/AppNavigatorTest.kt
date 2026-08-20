package com.secondpasslibrary.reader.app.shell

import androidx.navigation3.runtime.NavKey
import com.secondpasslibrary.reader.bookdetail.BookDetailNavigationIntent
import org.junit.Assert.assertEquals
import org.junit.Test

class AppNavigatorTest {
    @Test
    fun `top-level history destination is named Marginalia`() {
        assertEquals("Marginalia", AppDestination.Marginalia.label)
    }

    @Test
    fun `top-level selection replaces the current destination`() {
        val backStack = mutableListOf<NavKey>(AppDestination.Home)
        val navigator = AppNavigator(backStack)

        navigator.select(AppDestination.Library)
        navigator.select(AppDestination.Settings)

        assertEquals(listOf(AppDestination.Settings), backStack)
    }

    @Test
    fun `selecting the current destination does not duplicate it`() {
        val backStack = mutableListOf<NavKey>(AppDestination.Home)
        val navigator = AppNavigator(backStack)

        navigator.select(AppDestination.Home)

        assertEquals(listOf(AppDestination.Home), backStack)
    }

    @Test
    fun `Home search carries its query into a Library route`() {
        val backStack = mutableListOf<NavKey>(AppDestination.Home)
        val navigator = AppNavigator(backStack)

        navigator.openLibrarySearch("ursula le guin")

        assertEquals(listOf(LibrarySearchRoute("ursula le guin")), backStack)
        assertEquals(AppDestination.Library, backStack.single().topLevelDestination())
    }

    @Test
    fun `Library Book Detail route returns to live Library entry`() {
        val backStack = mutableListOf<NavKey>(AppDestination.Library)
        val navigator = AppNavigator(backStack)

        navigator.openBookDetail("book-1", BookDetailReturnTarget.Library)
        assertEquals(
            listOf(
                AppDestination.Library,
                BookDetailRoute("book-1", BookDetailReturnTarget.Library)
            ),
            backStack
        )

        navigator.goBack()
        assertEquals(listOf(AppDestination.Library), backStack)
    }

    @Test
    fun `Shelf Book Detail route retains typed Shelf return context`() {
        val backStack = mutableListOf<NavKey>(AppDestination.Shelves)
        val navigator = AppNavigator(backStack)
        val target = BookDetailReturnTarget.ShelfDetail(
            "shelf-1",
            ShelfCollectionOrigin.GROUP
        )

        navigator.openBookDetail("book-1", target)

        assertEquals(BookDetailRoute("book-1", target), backStack.last())
        assertEquals(AppDestination.Shelves, backStack.last().topLevelDestination())
        navigator.goBack()
        assertEquals(listOf(AppDestination.Shelves), backStack)
    }

    @Test
    fun `Book Detail metadata navigation creates typed Library routes`() {
        val backStack = mutableListOf<NavKey>(
            AppDestination.Shelves,
            BookDetailRoute(
                "book-1",
                BookDetailReturnTarget.ShelfDetail(
                    "shelf-1",
                    ShelfCollectionOrigin.SHARED
                )
            )
        )
        val navigator = AppNavigator(backStack)

        navigator.openLibraryAuthor("author-1")
        assertEquals(listOf(LibraryAuthorRoute("author-1")), backStack)

        navigator.openLibrarySeries("series-1")
        assertEquals(listOf(LibrarySeriesRoute("series-1")), backStack)

        navigator.openLibraryTag("tag-1", "fiction")
        assertEquals(listOf(LibraryTagRoute("tag-1", "fiction")), backStack)
    }

    @Test
    fun `Book Detail Manage Shelves intent enters Shelves as a new context`() {
        val backStack = mutableListOf<NavKey>(
            AppDestination.Library,
            BookDetailRoute("book-1", BookDetailReturnTarget.Library)
        )
        val navigator = AppNavigator(backStack)

        navigator.handleBookDetailNavigation(
            BookDetailNavigationIntent.ManageShelves,
            BookDetailRoute("book-1", BookDetailReturnTarget.Library)
        )

        assertEquals(listOf(AppDestination.Shelves), backStack)
    }

    @Test
    fun `Library Book Detail opens scoped Marginalia and restores the same detail route`() {
        val source = BookDetailRoute("book-1", BookDetailReturnTarget.Library)
        val backStack = mutableListOf<NavKey>(AppDestination.Library, source)
        val navigator = AppNavigator(backStack)

        navigator.handleBookDetailNavigation(
            BookDetailNavigationIntent.ReadingSessions("book-1"),
            source
        )

        assertEquals(
            BookMarginaliaRoute("book-1", MarginaliaReturnTarget.BookDetail(source)),
            backStack.last()
        )
        assertEquals(AppDestination.Marginalia, backStack.last().topLevelDestination())
        navigator.goBack()
        assertEquals(source, backStack.last())
    }

    @Test
    fun `Shelf Book Detail survives scoped Marginalia round trip`() {
        val source = BookDetailRoute(
            "book-1",
            BookDetailReturnTarget.ShelfDetail("shelf-1", ShelfCollectionOrigin.PERSONAL)
        )
        val backStack = mutableListOf<NavKey>(AppDestination.Shelves, source)
        val navigator = AppNavigator(backStack)

        navigator.openBookMarginalia("book-1", source)
        navigator.goBack()

        assertEquals(listOf(AppDestination.Shelves, source), backStack)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `scoped Marginalia cannot mismatch its Book Detail source`() {
        val source = BookDetailRoute("book-1", BookDetailReturnTarget.Library)
        AppNavigator(mutableListOf<NavKey>(source)).openBookMarginalia("book-2", source)
    }
}
