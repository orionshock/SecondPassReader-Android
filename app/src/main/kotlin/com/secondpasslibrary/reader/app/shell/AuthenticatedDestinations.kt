package com.secondpasslibrary.reader.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.secondpasslibrary.client.AuthenticatedContext
import com.secondpasslibrary.reader.app.AppSessionState
import com.secondpasslibrary.reader.app.authenticatedFeatureContext
import com.secondpasslibrary.reader.bookdetail.BookDetailNavigationIntent
import com.secondpasslibrary.reader.bookdetail.BookDetailStateHost
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.home.HomeScreen
import com.secondpasslibrary.reader.library.LibraryExternalNavigation
import com.secondpasslibrary.reader.library.LibraryStateHost
import com.secondpasslibrary.reader.library.books.LibraryBooksEntry
import com.secondpasslibrary.reader.marginalia.MarginaliaHistoryContext
import com.secondpasslibrary.reader.marginalia.MarginaliaStateHost
import com.secondpasslibrary.reader.marginalia.ReadingSessionDetailEntry
import com.secondpasslibrary.reader.settings.LinkedSettings
import com.secondpasslibrary.reader.shelves.ShelfBookNavigationRequest
import com.secondpasslibrary.reader.shelves.ShelvesCollection
import com.secondpasslibrary.reader.shelves.ShelvesStateHost

@Composable
internal fun AccountDestinations(
    session: AppSessionState.AccountShell,
    navigation: AppNavigationState,
    navigator: AppNavigator,
    onAuthenticationRejected: () -> Unit,
    onRetryConnection: () -> Unit,
    onRelinkAccount: () -> Unit,
    onForgetAccount: () -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier
) {
    val verifiedContext = session.authenticatedFeatureContext
    val entries =
        entryProvider<NavKey> {
            registerHomeEntry(session, navigator, onAuthenticationRejected)
            if (verifiedContext == null) {
                registerConnectionRequiredEntries(
                    session.authority,
                    onRetryConnection,
                    onRelinkAccount,
                    onForgetAccount,
                    onOpenDrawer
                )
            } else {
                val bindings =
                    AuthenticatedDestinationBindings(
                        session.profile,
                        verifiedContext,
                        navigator,
                        onAuthenticationRejected,
                        onOpenDrawer
                    )
                registerAuthenticatedTopLevelEntries(bindings)
                registerLibraryRouteEntries(bindings)
                registerSharedBookDetailEntry(bindings)
                registerBookMarginaliaEntry(bindings)
                registerReadingSessionDetailEntry(bindings)
            }
        }
    val decoratedEntries = retainedActiveEntries(navigation, entries)
    NavDisplay(
        entries = decoratedEntries,
        modifier = modifier,
        onBack = { navigator.goBack() }
    )
}

private data class AuthenticatedDestinationBindings(
    val profile: ConnectionProfile,
    val context: AuthenticatedContext,
    val navigator: AppNavigator,
    val onAuthenticationRejected: () -> Unit,
    val onOpenDrawer: () -> Unit
)

private fun EntryProviderScope<NavKey>.registerHomeEntry(
    session: AppSessionState.AccountShell,
    navigator: AppNavigator,
    onAuthenticationRejected: () -> Unit
) {
    entry(key = AppDestination.Home) {
        HomeDestination(session, navigator, onAuthenticationRejected)
    }
}

private fun EntryProviderScope<NavKey>.registerAuthenticatedTopLevelEntries(
    bindings: AuthenticatedDestinationBindings
) {
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
            appBarContext = route.returnTarget.appBarContextLabel(),
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

private fun EntryProviderScope<NavKey>.registerReadingSessionDetailEntry(
    bindings: AuthenticatedDestinationBindings
) {
    entry<ReadingSessionDetailRoute> { route ->
        MarginaliaStateHost(
            profile = bindings.profile,
            detailEntry =
                ReadingSessionDetailEntry(
                    route.sessionId,
                    route.action.toMarginaliaEntryAction()
                ),
            onOpenDrawer = bindings.onOpenDrawer,
            onBackFromDetail = bindings.navigator::goBack,
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
    session: AppSessionState.AccountShell,
    navigator: AppNavigator,
    onAuthenticationRejected: () -> Unit
) {
    HomeScreen(
        profile = session.profile,
        profileId = session.profileId,
        verifiedContext = session.authenticatedFeatureContext,
        onNavigation = navigator::handleHomeNavigation,
        onAuthenticationRejected = onAuthenticationRejected
    )
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
