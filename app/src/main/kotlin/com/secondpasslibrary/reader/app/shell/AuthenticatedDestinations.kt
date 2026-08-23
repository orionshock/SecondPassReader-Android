package com.secondpasslibrary.reader.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.secondpasslibrary.client.AuthenticatedContext
import com.secondpasslibrary.reader.bookdetail.BookDetailNavigationIntent
import com.secondpasslibrary.reader.bookdetail.BookDetailStateHost
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.home.HomeNavigationIntent
import com.secondpasslibrary.reader.home.HomeScreen
import com.secondpasslibrary.reader.library.LibraryExternalNavigation
import com.secondpasslibrary.reader.library.LibraryStateHost
import com.secondpasslibrary.reader.library.books.LibraryBooksEntry
import com.secondpasslibrary.reader.marginalia.MarginaliaHistoryContext
import com.secondpasslibrary.reader.marginalia.MarginaliaStateHost
import com.secondpasslibrary.reader.settings.LinkedSettings
import com.secondpasslibrary.reader.shelves.ShelfBookNavigationRequest
import com.secondpasslibrary.reader.shelves.ShelvesCollection
import com.secondpasslibrary.reader.shelves.ShelvesStateHost

@Composable
internal fun AuthenticatedDestinations(
    profile: ConnectionProfile,
    context: AuthenticatedContext,
    backStack: MutableList<NavKey>,
    navigator: AppNavigator,
    onAuthenticationRejected: () -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier
) {
    val bindings = AuthenticatedDestinationBindings(
        profile,
        context,
        navigator,
        onAuthenticationRejected,
        onOpenDrawer
    )
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = navigator::goBack,
        entryProvider = entryProvider {
            registerTopLevelEntries(bindings)
            registerLibraryRouteEntries(bindings)
            registerSharedBookDetailEntry(bindings)
            registerBookMarginaliaEntry(bindings)
        }
    )
}

private data class AuthenticatedDestinationBindings(
    val profile: ConnectionProfile,
    val context: AuthenticatedContext,
    val navigator: AppNavigator,
    val onAuthenticationRejected: () -> Unit,
    val onOpenDrawer: () -> Unit
)

private fun EntryProviderScope<NavKey>.registerTopLevelEntries(
    bindings: AuthenticatedDestinationBindings
) {
    entry(key = AppDestination.Home) {
        HomeDestination(
            bindings.profile,
            bindings.context,
            bindings.navigator,
            bindings.onAuthenticationRejected
        )
    }
    entry(key = AppDestination.Shelves) {
        ShelvesStateHost(
            bindings.profile,
            bindings.onOpenDrawer,
            onBookSelected = { bindings.navigator.openShelfBook(it) },
            onAuthenticationRejected = bindings.onAuthenticationRejected
        )
    }
    entry(key = AppDestination.Marginalia) {
        MarginaliaStateHost(
            profile = bindings.profile,
            onOpenDrawer = bindings.onOpenDrawer,
            onAuthenticationRejected = bindings.onAuthenticationRejected
        )
    }
    entry(key = AppDestination.Settings) { LinkedSettings(bindings.profile, bindings.context) }
}

private fun EntryProviderScope<NavKey>.registerLibraryRouteEntries(
    bindings: AuthenticatedDestinationBindings
) {
    entry(key = AppDestination.Library) {
        LibraryDestination(bindings, LibraryBooksEntry.Browse)
    }
    entry<LibrarySearchRoute> { route ->
        LibraryDestination(bindings, LibraryBooksEntry.BroadSearch(route.query))
    }
    entry<LibraryAuthorRoute> { route ->
        LibraryDestination(
            bindings,
            LibraryBooksEntry.Browse,
            LibraryExternalNavigation.Author(route.authorId)
        )
    }
    entry<LibrarySeriesRoute> { route ->
        LibraryDestination(
            bindings,
            LibraryBooksEntry.Browse,
            LibraryExternalNavigation.Series(route.seriesId)
        )
    }
    entry<LibraryTagRoute> { route ->
        LibraryDestination(
            bindings,
            LibraryBooksEntry.Browse,
            LibraryExternalNavigation.Tag(route.tagId, route.tagSlug)
        )
    }
}

private fun EntryProviderScope<NavKey>.registerSharedBookDetailEntry(
    bindings: AuthenticatedDestinationBindings
) {
    entry<BookDetailRoute> { route ->
        BookDetailStateHost(
            profile = bindings.profile,
            bookId = route.bookId,
            onBack = bindings.navigator::goBack,
            onNavigation = { intent ->
                bindings.navigator.handleBookDetailNavigation(intent, route)
            },
            onAuthenticationRejected = bindings.onAuthenticationRejected
        )
    }
}

private fun EntryProviderScope<NavKey>.registerBookMarginaliaEntry(
    bindings: AuthenticatedDestinationBindings
) {
    entry<BookMarginaliaRoute> { route ->
        MarginaliaStateHost(
            profile = bindings.profile,
            initialContext = MarginaliaHistoryContext.Book(route.bookId),
            onOpenDrawer = bindings.onOpenDrawer,
            onBackFromHistory = bindings.navigator::goBack,
            onAuthenticationRejected = bindings.onAuthenticationRejected
        )
    }
}

@Composable
private fun LibraryDestination(
    bindings: AuthenticatedDestinationBindings,
    entry: LibraryBooksEntry,
    externalNavigation: LibraryExternalNavigation? = null
) {
    LibraryStateHost(
        bindings.profile,
        entry,
        bindings.context.serverInfo.advancedLibraryGroupsEnabled,
        bindings.onAuthenticationRejected,
        onBookSelected = {
            bindings.navigator.openBookDetail(it, BookDetailReturnTarget.Library)
        },
        externalNavigation
    )
}

@Composable
private fun HomeDestination(
    profile: ConnectionProfile,
    context: AuthenticatedContext,
    navigator: AppNavigator,
    onAuthenticationRejected: () -> Unit
) {
    HomeScreen(
        profile = profile,
        profileId = context.currentUser.profileId,
        onNavigation = navigator::handleHomeNavigation,
        onAuthenticationRejected = onAuthenticationRejected
    )
}

private fun AppNavigator.handleHomeNavigation(intent: HomeNavigationIntent) {
    when (intent) {
        is HomeNavigationIntent.LibrarySearch -> openLibrarySearch(intent.query)
        HomeNavigationIntent.OpenShelves -> select(AppDestination.Shelves)
        HomeNavigationIntent.ViewAllSessions -> select(AppDestination.Marginalia)
    }
}

private fun AppNavigator.openShelfBook(request: ShelfBookNavigationRequest) {
    openBookDetail(
        request.bookId,
        BookDetailReturnTarget.ShelfDetail(request.shelfId, request.origin.toRouteOrigin())
    )
}

private fun ShelvesCollection.toRouteOrigin(): ShelfCollectionOrigin = when (this) {
    ShelvesCollection.PERSONAL -> ShelfCollectionOrigin.PERSONAL
    ShelvesCollection.SHARED -> ShelfCollectionOrigin.SHARED
    ShelvesCollection.GROUP -> ShelfCollectionOrigin.GROUP
}
