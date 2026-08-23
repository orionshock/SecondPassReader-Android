package com.secondpasslibrary.reader.app.shell

import com.secondpasslibrary.reader.bookdetail.BookDetailNavigationIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigatorTest {
    @Test
    fun `each top-level destination owns a distinct rooted stack`() {
        val navigation = appNavigationStateForTest()
        val stacks = AppDestination.entries.map(navigation::backStack)

        AppDestination.entries.forEach { destination ->
            assertEquals(listOf(destination), navigation.backStack(destination))
        }
        stacks.indices.forEach { index ->
            stacks.drop(index + 1).forEach { other -> assertNotSame(stacks[index], other) }
        }
    }

    @Test
    fun `top-level selection retains inactive stack and restores its route`() {
        val navigation = appNavigationStateForTest(AppDestination.Library)
        val navigator = AppNavigator(navigation)
        navigator.openBookDetail("book-1", BookDetailReturnTarget.Library)
        val retainedLibraryStack = navigation.activeBackStack

        navigator.select(AppDestination.Home)
        navigator.select(AppDestination.Library)

        assertSame(retainedLibraryStack, navigation.activeBackStack)
        assertEquals(
            BookDetailRoute("book-1", BookDetailReturnTarget.Library),
            navigation.currentRoute
        )
    }

    @Test
    fun `selecting current top-level destination does not duplicate root`() {
        val navigation = appNavigationStateForTest()

        AppNavigator(navigation).select(AppDestination.Home)

        assertEquals(listOf(AppDestination.Home), navigation.activeBackStack)
    }

    @Test
    fun `Home search replaces only Library context and selects its stack`() {
        val navigation = appNavigationStateForTest(AppDestination.Library)
        navigation.push(BookDetailRoute("old-book", BookDetailReturnTarget.Library))
        navigation.select(AppDestination.Home)

        AppNavigator(navigation).openLibrarySearch("ursula le guin")

        assertEquals(AppDestination.Library, navigation.selectedDestination)
        assertEquals(
            listOf(AppDestination.Library, LibrarySearchRoute("ursula le guin")),
            navigation.activeBackStack
        )
        assertEquals(listOf(AppDestination.Home), navigation.backStack(AppDestination.Home))
    }

    @Test
    fun `Library Book Detail pops to its live Library entry`() {
        val navigation = appNavigationStateForTest(AppDestination.Library)
        val navigator = AppNavigator(navigation)

        navigator.openBookDetail("book-1", BookDetailReturnTarget.Library)
        assertEquals(
            listOf(
                AppDestination.Library,
                BookDetailRoute("book-1", BookDetailReturnTarget.Library)
            ),
            navigation.activeBackStack
        )

        assertTrue(navigator.goBack())
        assertEquals(listOf(AppDestination.Library), navigation.activeBackStack)
        assertFalse(navigator.goBack())
    }

    @Test
    fun `Shelf Book Detail remains on Shelves stack with typed return context`() {
        val navigation = appNavigationStateForTest(AppDestination.Shelves)
        val navigator = AppNavigator(navigation)
        val target = BookDetailReturnTarget.ShelfDetail("shelf-1", ShelfCollectionOrigin.GROUP)

        navigator.openBookDetail("book-1", target)

        assertEquals(BookDetailRoute("book-1", target), navigation.currentRoute)
        assertEquals(AppDestination.Shelves, navigation.currentRoute.topLevelDestination())
        navigator.goBack()
        assertEquals(listOf(AppDestination.Shelves), navigation.activeBackStack)
    }

    @Test
    fun `Book Detail metadata navigation explicitly transfers to Library stack`() {
        val navigation = appNavigationStateForTest(AppDestination.Shelves)
        val navigator = AppNavigator(navigation)
        navigation.push(shelfBookDetail())

        navigator.handleBookDetailNavigation(
            BookDetailNavigationIntent.Author("author-1"),
            shelfBookDetail()
        )
        assertEquals(
            listOf(AppDestination.Library, LibraryAuthorRoute("author-1")),
            navigation.activeBackStack
        )

        navigator.openLibrarySeries("series-1")
        assertEquals(
            listOf(AppDestination.Library, LibrarySeriesRoute("series-1")),
            navigation.activeBackStack
        )

        navigator.openLibraryTag("tag-1", "fiction")
        assertEquals(
            listOf(AppDestination.Library, LibraryTagRoute("tag-1", "fiction")),
            navigation.activeBackStack
        )
        assertEquals(listOf(AppDestination.Shelves), navigation.backStack(AppDestination.Shelves))
    }

    @Test
    fun `Manage Shelves starts Shelves root without clearing origin stack`() {
        val navigation = appNavigationStateForTest(AppDestination.Library)
        val source = BookDetailRoute("book-1", BookDetailReturnTarget.Library)
        navigation.push(source)

        AppNavigator(navigation).handleBookDetailNavigation(
            BookDetailNavigationIntent.ManageShelves,
            source
        )

        assertEquals(AppDestination.Shelves, navigation.selectedDestination)
        assertEquals(listOf(AppDestination.Shelves), navigation.activeBackStack)
        assertEquals(listOf(AppDestination.Library), navigation.backStack(AppDestination.Library))
    }

    @Test
    fun `Book-scoped Marginalia stays on Library Book Detail origin stack`() {
        val navigation = appNavigationStateForTest(AppDestination.Library)
        val navigator = AppNavigator(navigation)
        val source = BookDetailRoute("book-1", BookDetailReturnTarget.Library)
        navigation.push(source)

        navigator.openBookMarginalia("book-1", source)

        assertEquals(AppDestination.Library, navigation.selectedDestination)
        assertEquals(AppDestination.Library, navigation.currentRoute.topLevelDestination())
        assertEquals(
            BookMarginaliaRoute("book-1", MarginaliaReturnTarget.BookDetail(source)),
            navigation.currentRoute
        )
        navigator.goBack()
        assertEquals(source, navigation.currentRoute)
    }

    @Test
    fun `Book-scoped Marginalia stays on Shelves Book Detail origin stack`() {
        val navigation = appNavigationStateForTest(AppDestination.Shelves)
        val navigator = AppNavigator(navigation)
        val source = shelfBookDetail()
        navigation.push(source)

        navigator.openBookMarginalia("book-1", source)

        assertEquals(AppDestination.Shelves, navigation.selectedDestination)
        assertEquals(AppDestination.Shelves, navigation.currentRoute.topLevelDestination())
        navigator.goBack()
        assertEquals(source, navigation.currentRoute)
    }

    @Test
    fun `Book-scoped Marginalia suppresses origin stack shell top bar`() {
        val librarySource = BookDetailRoute("book-1", BookDetailReturnTarget.Library)
        val route =
            BookMarginaliaRoute(
                "book-1",
                MarginaliaReturnTarget.BookDetail(librarySource)
            )

        assertFalse(showsShellTopBar(AppDestination.Library, route))
        assertTrue(showsShellTopBar(AppDestination.Library, AppDestination.Library))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `scoped Marginalia cannot mismatch its Book Detail source`() {
        val navigation = appNavigationStateForTest(AppDestination.Library)
        AppNavigator(navigation).openBookMarginalia(
            "book-2",
            BookDetailRoute("book-1", BookDetailReturnTarget.Library)
        )
    }

    private fun shelfBookDetail() = BookDetailRoute(
        "book-1",
        BookDetailReturnTarget.ShelfDetail("shelf-1", ShelfCollectionOrigin.SHARED)
    )
}
