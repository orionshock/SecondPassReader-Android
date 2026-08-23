package com.secondpasslibrary.reader.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
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
import com.secondpasslibrary.reader.marginalia.MarginaliaExternalNavigationIntent
import com.secondpasslibrary.reader.marginalia.MarginaliaHistoryContext
import com.secondpasslibrary.reader.marginalia.MarginaliaStateHost
import com.secondpasslibrary.reader.marginalia.ReadingSessionDetailEntry
import com.secondpasslibrary.reader.settings.LinkedSettings
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
    val environment =
        rememberUpdatedState(
            AccountDestinationEnvironment(
                session,
                navigator,
                onAuthenticationRejected,
                onRetryConnection,
                onRelinkAccount,
                onForgetAccount,
                onOpenDrawer
            )
        )
    val entries =
        entryProvider<NavKey> {
            registerHomeEntry(environment)
            registerAuthenticatedTopLevelEntries(environment)
            registerShelfDetailEntry(environment)
            registerLibraryRouteEntries(environment)
            registerSharedBookDetailEntry(environment)
            registerBookMarginaliaEntry(environment)
            registerReadingSessionDetailEntry(environment)
        }
    val decoratedEntries = retainedActiveEntries(navigation, entries)
    NavDisplay(
        entries = decoratedEntries,
        modifier = modifier,
        onBack = { navigator.goBack() }
    )
}

internal data class AccountDestinationEnvironment(
    val session: AppSessionState.AccountShell,
    val navigator: AppNavigator,
    val onAuthenticationRejected: () -> Unit,
    val onRetryConnection: () -> Unit,
    val onRelinkAccount: () -> Unit,
    val onForgetAccount: () -> Unit,
    val onOpenDrawer: () -> Unit
)

internal data class AuthenticatedDestinationBindings(
    val profile: ConnectionProfile,
    val context: AuthenticatedContext,
    val navigator: AppNavigator,
    val onAuthenticationRejected: () -> Unit,
    val onOpenDrawer: () -> Unit
)

private fun EntryProviderScope<NavKey>.registerHomeEntry(
    environment: State<AccountDestinationEnvironment>
) {
    entry(key = AppDestination.Home) {
        val current = environment.value
        HomeDestination(
            current.session,
            current.navigator,
            current.onAuthenticationRejected
        )
    }
}

private fun EntryProviderScope<NavKey>.registerAuthenticatedTopLevelEntries(
    environment: State<AccountDestinationEnvironment>
) {
    entry(key = AppDestination.Shelves) {
        AuthenticatedDestination(environment) { bindings ->
            ShelvesStateHost(
                bindings.profile,
                bindings.onOpenDrawer,
                onBookSelected = { bindings.navigator.openShelfBook(it) },
                onAuthenticationRejected = bindings.onAuthenticationRejected
            )
        }
    }
    entry(key = AppDestination.Marginalia) {
        AuthenticatedDestination(environment) { bindings ->
            MarginaliaStateHost(
                profile = bindings.profile,
                onOpenDrawer = bindings.onOpenDrawer,
                onNavigation = { intent ->
                    bindings.navigator.handleMarginaliaNavigation(
                        intent,
                        BookDetailReturnTarget.Marginalia
                    )
                },
                onAuthenticationRejected = bindings.onAuthenticationRejected
            )
        }
    }
    entry(key = AppDestination.Settings) {
        AuthenticatedDestination(environment) { bindings ->
            LinkedSettings(bindings.profile, bindings.context)
        }
    }
}

private fun EntryProviderScope<NavKey>.registerLibraryRouteEntries(
    environment: State<AccountDestinationEnvironment>
) {
    entry(key = AppDestination.Library) {
        AuthenticatedDestination(environment) { bindings ->
            LibraryDestination(bindings, LibraryBooksEntry.Browse)
        }
    }
    entry<LibrarySearchRoute> { route ->
        AuthenticatedDestination(environment) { bindings ->
            LibraryDestination(bindings, LibraryBooksEntry.BroadSearch(route.query))
        }
    }
    entry<LibraryAuthorRoute> { route ->
        AuthenticatedDestination(environment) { bindings ->
            LibraryDestination(
                bindings,
                LibraryBooksEntry.Browse,
                LibraryExternalNavigation.Author(route.authorId)
            )
        }
    }
    entry<LibrarySeriesRoute> { route ->
        AuthenticatedDestination(environment) { bindings ->
            LibraryDestination(
                bindings,
                LibraryBooksEntry.Browse,
                LibraryExternalNavigation.Series(route.seriesId)
            )
        }
    }
    entry<LibraryTagRoute> { route ->
        AuthenticatedDestination(environment) { bindings ->
            LibraryDestination(
                bindings,
                LibraryBooksEntry.Browse,
                LibraryExternalNavigation.Tag(route.tagId, route.tagSlug)
            )
        }
    }
}

private fun EntryProviderScope<NavKey>.registerSharedBookDetailEntry(
    environment: State<AccountDestinationEnvironment>
) {
    entry<BookDetailRoute> { route ->
        AuthenticatedDestination(environment) { bindings ->
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
}

private fun EntryProviderScope<NavKey>.registerBookMarginaliaEntry(
    environment: State<AccountDestinationEnvironment>
) {
    entry<BookMarginaliaRoute> { route ->
        AuthenticatedDestination(environment) { bindings ->
            MarginaliaStateHost(
                profile = bindings.profile,
                initialContext = MarginaliaHistoryContext.Book(route.bookId),
                onOpenDrawer = bindings.onOpenDrawer,
                onBackFromHistory = bindings.navigator::goBack,
                onNavigation = { intent ->
                    bindings.navigator.handleMarginaliaNavigation(
                        intent,
                        BookDetailReturnTarget.BookMarginalia(route)
                    )
                },
                onAuthenticationRejected = bindings.onAuthenticationRejected
            )
        }
    }
}

private fun EntryProviderScope<NavKey>.registerReadingSessionDetailEntry(
    environment: State<AccountDestinationEnvironment>
) {
    entry<ReadingSessionDetailRoute> { route ->
        AuthenticatedDestination(environment) { bindings ->
            MarginaliaStateHost(
                profile = bindings.profile,
                detailEntry =
                    ReadingSessionDetailEntry(
                        route.sessionId,
                        route.action.toMarginaliaEntryAction()
                    ),
                onOpenDrawer = bindings.onOpenDrawer,
                onBackFromDetail = bindings.navigator::goBack,
                onNavigation = { intent ->
                    bindings.navigator.handleMarginaliaNavigation(
                        intent,
                        BookDetailReturnTarget.ReadingSessionDetail(route)
                    )
                },
                onAuthenticationRejected = bindings.onAuthenticationRejected
            )
        }
    }
}

private fun AppNavigator.handleMarginaliaNavigation(
    intent: MarginaliaExternalNavigationIntent,
    returnTarget: BookDetailReturnTarget
) {
    when (intent) {
        is MarginaliaExternalNavigationIntent.BookDetail ->
            openBookDetail(intent.bookId, returnTarget)

        is MarginaliaExternalNavigationIntent.Reader -> Unit
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
        bindings.onOpenDrawer,
        onBookSelected = {
            bindings.navigator.openBookDetail(it, BookDetailReturnTarget.Library)
        },
        onBookAction = bindings.navigator::handleLibraryBookAction,
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
