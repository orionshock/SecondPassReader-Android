package com.secondpasslibrary.reader.app.shell

import com.secondpasslibrary.reader.bookdetail.BookDetailNavigationIntent
import com.secondpasslibrary.reader.design.book.BookCardAction
import com.secondpasslibrary.reader.home.HomeNavigationIntent
import com.secondpasslibrary.reader.home.HomeShelfOrigin
import com.secondpasslibrary.reader.home.OpenReaderIntent
import com.secondpasslibrary.reader.marginalia.MarginaliaExternalNavigationIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigatorTest {
    @Test
    fun `offline Book details stays on Settings stack and Back returns to Settings`() {
        val navigation = appNavigationStateForTest(AppDestination.Settings)
        val navigator = AppNavigator(navigation)

        navigator.openBookDetail("book-1", BookDetailReturnTarget.Settings)

        assertEquals(AppDestination.Settings, navigation.currentRoute.topLevelDestination())
        assertEquals(
            BookDetailRoute("book-1", BookDetailReturnTarget.Settings),
            navigation.currentRoute
        )
        assertTrue(navigation.pop())
        assertEquals(AppDestination.Settings, navigation.currentRoute)
    }

    @Test
    fun `Home Shelf navigation targets typed Shelf Detail on Shelves stack`() {
        val navigation = appNavigationStateForTest(AppDestination.Home)

        AppNavigator(navigation).handleHomeNavigation(
            HomeNavigationIntent.OpenShelfDetail("shelf-1", HomeShelfOrigin.GROUP)
        )

        assertEquals(AppDestination.Shelves, navigation.selectedDestination)
        assertEquals(
            listOf(
                AppDestination.Shelves,
                ShelfDetailRoute("shelf-1", ShelfCollectionOrigin.GROUP)
            ),
            navigation.activeBackStack
        )
        assertEquals(listOf(AppDestination.Home), navigation.backStack(AppDestination.Home))
    }

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
        assertTrue(navigator.goBack())
        assertEquals(AppDestination.Home, navigation.selectedDestination)
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
    fun `Book Detail labels open filtered Library and Back restores exact source route`() {
        val labels = listOf(
            BookDetailNavigationIntent.Author("author-1") to
                LibraryAuthorRoute("author-1", shelfBookDetail()),
            BookDetailNavigationIntent.Series("series-1") to
                LibrarySeriesRoute("series-1", shelfBookDetail()),
            BookDetailNavigationIntent.Tag("tag-1", "fiction") to
                LibraryTagRoute("tag-1", "fiction", shelfBookDetail())
        )
        labels.forEach { (intent, expectedRoute) ->
            val navigation = appNavigationStateForTest(AppDestination.Shelves)
            val navigator = AppNavigator(navigation)
            val source = shelfBookDetail()
            navigation.push(source)

            navigator.handleBookDetailNavigation(intent, source)
            assertEquals(AppDestination.Library, navigation.selectedDestination)
            assertEquals(expectedRoute, navigation.currentRoute)
            assertEquals(
                listOf(AppDestination.Shelves, source),
                navigation.backStack(AppDestination.Shelves)
            )
            assertTrue(navigator.goBack())
            assertEquals(AppDestination.Shelves, navigation.selectedDestination)
            assertEquals(source, navigation.currentRoute)
            assertEquals(
                listOf(AppDestination.Library),
                navigation.backStack(AppDestination.Library)
            )
        }
    }

    @Test
    fun `Library Book Detail label pushes filter above same detail`() {
        val navigation = appNavigationStateForTest(AppDestination.Library)
        val navigator = AppNavigator(navigation)
        val source = BookDetailRoute("book-1", BookDetailReturnTarget.Library)
        navigation.push(source)

        navigator.handleBookDetailNavigation(
            BookDetailNavigationIntent.Tag("tag-1", "fiction"),
            source
        )

        assertEquals(LibraryTagRoute("tag-1", "fiction", source), navigation.currentRoute)
        assertTrue(navigator.goBack())
        assertEquals(source, navigation.currentRoute)
    }

    @Test
    fun `non Home roots return Home only after nested routes pop`() {
        val routes = mapOf<AppDestination, AppRoute>(
            AppDestination.Library to LibrarySearchRoute("query"),
            AppDestination.Shelves to ShelfDetailRoute("shelf-1", ShelfCollectionOrigin.PERSONAL),
            AppDestination.Marginalia to
                BookDetailRoute("book-1", BookDetailReturnTarget.Marginalia),
            AppDestination.Settings to BookDetailRoute("book-1", BookDetailReturnTarget.Settings)
        )
        routes.forEach { (destination, route) ->
            val navigation = appNavigationStateForTest(destination)
            val navigator = AppNavigator(navigation)
            navigation.push(route)
            assertTrue(navigator.goBack())
            assertEquals(destination, navigation.currentRoute)
            assertTrue(navigator.goBack())
            assertEquals(AppDestination.Home, navigation.selectedDestination)
            assertEquals(listOf(destination), navigation.backStack(destination))
            assertFalse(navigator.goBack())
        }
    }

    @Test
    fun `Back to Home retains other destination stack for drawer return`() {
        val navigation = appNavigationStateForTest(AppDestination.Library)
        val navigator = AppNavigator(navigation)
        val detail = BookDetailRoute("book-1", BookDetailReturnTarget.Library)
        navigation.push(detail)
        navigator.select(AppDestination.Settings)

        assertTrue(navigator.goBack())
        assertEquals(AppDestination.Home, navigation.selectedDestination)
        navigator.select(AppDestination.Library)
        assertEquals(detail, navigation.currentRoute)
    }

    @Test
    fun `About root Back returns Home then leaves Android exit to shell`() {
        val navigation = appNavigationStateForTest()
        val navigator = AppNavigator(navigation)
        navigator.select(AppDestination.About)
        assertEquals(AppDestination.About, navigation.currentRoute)
        assertTrue(navigator.goBack())
        assertEquals(AppDestination.Home, navigation.currentRoute)
        assertFalse(navigator.goBack())
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
    fun `Reader is pushed on its Book Detail origin stack and Back restores Book Detail`() {
        val navigation = appNavigationStateForTest(AppDestination.Library)
        val navigator = AppNavigator(navigation)
        val source = BookDetailRoute("book-1", BookDetailReturnTarget.Library)
        navigation.push(source)

        navigator.handleBookDetailNavigation(BookDetailNavigationIntent.ReadBook("book-1"), source)

        assertEquals(
            ReaderRoute("book-1", ReaderReturnTarget.BookDetail(source)),
            navigation.currentRoute
        )
        assertEquals(AppDestination.Library, navigation.currentRoute.topLevelDestination())
        assertFalse(showsShellTopBar(AppDestination.Library, navigation.currentRoute))
        assertTrue(navigator.goBack())
        assertEquals(source, navigation.currentRoute)
    }

    @Test
    fun `Home Reading History opens Reader directly and Back restores Home`() {
        val navigation = appNavigationStateForTest(AppDestination.Home)
        val navigator = AppNavigator(navigation)

        navigator.handleHomeNavigation(
            OpenReaderIntent("book-1", "session-1")
        )

        assertEquals(
            listOf(
                AppDestination.Home,
                ReaderRoute("book-1", ReaderReturnTarget.Home, "session-1")
            ),
            navigation.activeBackStack
        )
        assertEquals(AppDestination.Home, navigation.currentRoute.topLevelDestination())
        assertTrue(navigator.goBack())
        assertEquals(listOf(AppDestination.Home), navigation.activeBackStack)
    }

    @Test
    fun `Book Marginalia opens existing Session in Reader and Back restores exact route`() {
        val navigation = appNavigationStateForTest(AppDestination.Library)
        val navigator = AppNavigator(navigation)
        val bookDetail = BookDetailRoute("book-1", BookDetailReturnTarget.Library)
        val marginalia =
            BookMarginaliaRoute("book-1", MarginaliaReturnTarget.BookDetail(bookDetail))
        navigation.push(bookDetail)
        navigation.push(marginalia)

        navigator.handleBookMarginaliaNavigation(
            MarginaliaExternalNavigationIntent.Reader("book-1", "session-1"),
            marginalia
        )

        assertEquals(
            ReaderRoute(
                bookId = "book-1",
                returnTarget = ReaderReturnTarget.BookMarginalia(marginalia),
                existingSessionId = "session-1"
            ),
            navigation.currentRoute
        )
        assertEquals(AppDestination.Library, navigation.currentRoute.topLevelDestination())
        assertTrue(navigator.goBack())
        assertEquals(marginalia, navigation.currentRoute)
    }

    @Test
    fun `Reading Session detail opens its Session in Reader and Back restores same detail`() {
        val navigation = appNavigationStateForTest(AppDestination.Home)
        val navigator = AppNavigator(navigation)
        val detail =
            ReadingSessionDetailRoute(
                "session-1",
                ReadingSessionDetailReturnTarget.Home
            )
        navigation.push(detail)

        navigator.handleReadingSessionDetailNavigation(
            MarginaliaExternalNavigationIntent.Reader("book-1", "session-1"),
            detail
        )

        assertEquals(
            ReaderRoute(
                bookId = "book-1",
                returnTarget = ReaderReturnTarget.ReadingSessionDetail(detail),
                existingSessionId = "session-1"
            ),
            navigation.currentRoute
        )
        assertEquals(AppDestination.Home, navigation.currentRoute.topLevelDestination())
        assertTrue(navigator.goBack())
        assertEquals(detail, navigation.currentRoute)
    }

    @Test
    fun `top-level Marginalia Reader returns to retained Marginalia route`() {
        val navigation = appNavigationStateForTest(AppDestination.Marginalia)
        val navigator = AppNavigator(navigation)

        navigator.handleTopLevelMarginaliaNavigation(
            MarginaliaExternalNavigationIntent.Reader("book-1", "session-1")
        )

        assertEquals(
            ReaderRoute(
                bookId = "book-1",
                returnTarget = ReaderReturnTarget.Marginalia,
                existingSessionId = "session-1"
            ),
            navigation.currentRoute
        )
        assertTrue(navigator.goBack())
        assertEquals(AppDestination.Marginalia, navigation.currentRoute)
    }

    @Test
    fun `Home details stay on Home stack with typed return context`() {
        val navigation = appNavigationStateForTest()
        val navigator = AppNavigator(navigation)

        navigator.openBookDetail("book-1", BookDetailReturnTarget.Home)
        assertEquals(
            BookDetailRoute("book-1", BookDetailReturnTarget.Home),
            navigation.currentRoute
        )
        assertEquals(AppDestination.Home, navigation.currentRoute.topLevelDestination())
        navigator.goBack()
        assertEquals(listOf(AppDestination.Home), navigation.activeBackStack)
    }

    @Test
    fun `Library Book actions use existing detail history and axis routes`() {
        val navigation = appNavigationStateForTest(AppDestination.Library)
        val navigator = AppNavigator(navigation)

        navigator.handleLibraryBookAction(BookCardAction.ReadingSessions("book-1"))
        assertEquals(
            BookMarginaliaRoute("book-1", MarginaliaReturnTarget.Library),
            navigation.currentRoute
        )
        navigator.goBack()

        navigator.handleLibraryBookAction(BookCardAction.Author("book-1", "author-1", "Author"))
        assertEquals(LibraryAuthorRoute("author-1"), navigation.currentRoute)

        navigator.handleLibraryBookAction(BookCardAction.Series("book-1", "series-1", "Series"))
        assertEquals(LibrarySeriesRoute("series-1"), navigation.currentRoute)
    }

    @Test
    fun `Home Reading Session actions use shared detail route on Home stack`() {
        val navigation = appNavigationStateForTest()
        val navigator = AppNavigator(navigation)

        navigator.openReadingSessionDetail(
            "session-1",
            ReadingSessionDetailReturnTarget.Home,
            ReadingSessionDetailRouteAction.EDIT
        )

        assertEquals(
            ReadingSessionDetailRoute(
                "session-1",
                ReadingSessionDetailReturnTarget.Home,
                ReadingSessionDetailRouteAction.EDIT
            ),
            navigation.currentRoute
        )
        assertEquals(AppDestination.Home, navigation.currentRoute.topLevelDestination())
        assertFalse(showsShellTopBar(AppDestination.Home, navigation.currentRoute))
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
        assertFalse(showsShellTopBar(AppDestination.Library, AppDestination.Library))
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
