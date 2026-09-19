package com.secondpasslibrary.reader.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.secondpasslibrary.client.AuthenticatedContext
import com.secondpasslibrary.reader.app.AppAvailability
import com.secondpasslibrary.reader.app.AppSessionAuthority
import com.secondpasslibrary.reader.app.AppSessionState
import com.secondpasslibrary.reader.app.authenticatedFeatureContext
import com.secondpasslibrary.reader.bookdetail.BookDetailNavigationIntent
import com.secondpasslibrary.reader.bookdetail.BookDetailStateHost
import com.secondpasslibrary.reader.connection.ConnectionLifecycleActionState
import com.secondpasslibrary.reader.connection.ConnectionLifecycleActions
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.home.HomeRefreshAvailability
import com.secondpasslibrary.reader.home.HomeScreen
import com.secondpasslibrary.reader.library.LibraryExternalNavigation
import com.secondpasslibrary.reader.library.LibraryStateHost
import com.secondpasslibrary.reader.library.books.LibraryBooksEntry
import com.secondpasslibrary.reader.marginalia.MarginaliaHistoryContext
import com.secondpasslibrary.reader.marginalia.MarginaliaStateHost
import com.secondpasslibrary.reader.marginalia.ReadingSessionDetailEntry
import com.secondpasslibrary.reader.settings.LinkedSettings
import com.secondpasslibrary.reader.settings.SettingsConnectionStatus
import com.secondpasslibrary.reader.shelves.ShelvesStateHost

@Composable
internal fun AccountDestinations(
    session: AppSessionState.AccountShell,
    lifecycleActionState: ConnectionLifecycleActionState,
    lifecycleActions: ConnectionLifecycleActions,
    navigation: AppNavigationState,
    navigator: AppNavigator,
    onAuthenticationRejected: () -> Unit,
    onHomeRefreshAvailabilityChanged: (HomeRefreshAvailability) -> Unit,
    onCheckConnection: suspend () -> Boolean,
    onWorkOffline: () -> Unit,
    onReconnect: () -> Unit,
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
                lifecycleActionState,
                lifecycleActions,
                navigator,
                onAuthenticationRejected,
                onHomeRefreshAvailabilityChanged,
                onCheckConnection,
                onWorkOffline,
                onReconnect,
                onRetryConnection,
                onRelinkAccount,
                onForgetAccount,
                onOpenDrawer
            )
        )
    val entries =
        entryProvider<AppRoute> {
            registerHomeEntry(environment)
            registerAuthenticatedTopLevelEntries(environment)
            registerShelfDetailEntry(environment)
            registerLibraryRouteEntries(environment)
            registerSharedBookDetailEntry(environment)
            registerReaderEntry(environment)
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
    val lifecycleActionState: ConnectionLifecycleActionState,
    val lifecycleActions: ConnectionLifecycleActions,
    val navigator: AppNavigator,
    val onAuthenticationRejected: () -> Unit,
    val onHomeRefreshAvailabilityChanged: (HomeRefreshAvailability) -> Unit,
    val onCheckConnection: suspend () -> Boolean,
    val onWorkOffline: () -> Unit,
    val onReconnect: () -> Unit,
    val onRetryConnection: () -> Unit,
    val onRelinkAccount: () -> Unit,
    val onForgetAccount: () -> Unit,
    val onOpenDrawer: () -> Unit
)

internal data class AuthenticatedDestinationBindings(
    val profile: ConnectionProfile,
    val context: AuthenticatedContext,
    val navigator: AppNavigator,
    val serverMutationsAvailable: Boolean,
    val onAuthenticationRejected: () -> Unit,
    val onOpenDrawer: () -> Unit
)

private fun EntryProviderScope<AppRoute>.registerHomeEntry(
    environment: State<AccountDestinationEnvironment>
) {
    entry(key = AppDestination.Home) {
        val current = environment.value
        HomeDestination(
            current.session,
            current.navigator,
            current.onAuthenticationRejected,
            current.onHomeRefreshAvailabilityChanged,
            current.onCheckConnection
        )
    }
}

private fun EntryProviderScope<AppRoute>.registerAuthenticatedTopLevelEntries(
    environment: State<AccountDestinationEnvironment>
) {
    entry(key = AppDestination.Shelves) {
        AuthenticatedDestination(environment) { bindings ->
            ShelvesStateHost(
                bindings.profile,
                bindings.serverMutationsAvailable,
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
                onNavigation = bindings.navigator::handleTopLevelMarginaliaNavigation,
                onAuthenticationRejected = bindings.onAuthenticationRejected
            )
        }
    }
    entry(key = AppDestination.Settings) {
        val current = environment.value
        LinkedSettings(
            profile = current.session.profile,
            profileId = current.session.profileId,
            context = current.session.authenticatedFeatureContext,
            status = current.session.authority.toSettingsConnectionStatus(),
            availability = current.session.availability,
            checkingConnection =
                current.session.authority == AppSessionAuthority.CheckingConnection,
            onWorkOffline = current.onWorkOffline,
            onReconnect = current.onReconnect,
            lifecycleActionState = current.lifecycleActionState,
            lifecycleActions = current.lifecycleActions
        )
    }
}

private fun EntryProviderScope<AppRoute>.registerLibraryRouteEntries(
    environment: State<AccountDestinationEnvironment>
) {
    entry(key = AppDestination.Library) {
        LibraryDestination(environment.value, LibraryBooksEntry.Browse)
    }
    entry<LibrarySearchRoute> { route ->
        LibraryDestination(environment.value, LibraryBooksEntry.BroadSearch(route.query))
    }
    entry<LibraryAuthorRoute> { route ->
        LibraryDestination(
            environment.value,
            LibraryBooksEntry.Browse,
            LibraryExternalNavigation.Author(route.authorId)
        )
    }
    entry<LibrarySeriesRoute> { route ->
        LibraryDestination(
            environment.value,
            LibraryBooksEntry.Browse,
            LibraryExternalNavigation.Series(route.seriesId)
        )
    }
    entry<LibraryTagRoute> { route ->
        LibraryDestination(
            environment.value,
            LibraryBooksEntry.Browse,
            LibraryExternalNavigation.Tag(route.tagId, route.tagSlug)
        )
    }
}

private fun EntryProviderScope<AppRoute>.registerSharedBookDetailEntry(
    environment: State<AccountDestinationEnvironment>
) {
    entry<BookDetailRoute> { route ->
        AuthenticatedDestination(environment) { bindings ->
            BookDetailStateHost(
                profile = bindings.profile,
                profileId = environment.value.session.profileId,
                availability = environment.value.session.availability,
                serverMutationsAvailable = bindings.serverMutationsAvailable,
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

private fun EntryProviderScope<AppRoute>.registerBookMarginaliaEntry(
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
                    bindings.navigator.handleBookMarginaliaNavigation(intent, route)
                },
                onAuthenticationRejected = bindings.onAuthenticationRejected
            )
        }
    }
}

private fun EntryProviderScope<AppRoute>.registerReadingSessionDetailEntry(
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
                    bindings.navigator.handleReadingSessionDetailNavigation(intent, route)
                },
                onAuthenticationRejected = bindings.onAuthenticationRejected
            )
        }
    }
}

@Composable
private fun LibraryDestination(
    environment: AccountDestinationEnvironment,
    entry: LibraryBooksEntry,
    externalNavigation: LibraryExternalNavigation? = null
) {
    val session = environment.session
    val context = session.authenticatedFeatureContext
    if (session.availability !is AppAvailability.Offline && context == null) {
        ConnectionRequiredDestination(
            session.authority,
            environment.onRetryConnection,
            environment.onRelinkAccount,
            environment.onForgetAccount,
            environment.onOpenDrawer
        )
        return
    }
    LibraryStateHost(
        profile = session.profile,
        profileId = session.profileId,
        availability = session.availability,
        entry = entry,
        advancedGroupsEnabled = context?.serverInfo?.advancedLibraryGroupsEnabled == true,
        onAuthenticationRejected = environment.onAuthenticationRejected,
        onOpenDrawer = environment.onOpenDrawer,
        onBookSelected = {
            environment.navigator.openBookDetail(it, BookDetailReturnTarget.Library)
        },
        onBookAction = environment.navigator::handleLibraryBookAction,
        externalNavigation = externalNavigation
    )
}

@Composable
private fun HomeDestination(
    session: AppSessionState.AccountShell,
    navigator: AppNavigator,
    onAuthenticationRejected: () -> Unit,
    onRefreshAvailabilityChanged: (HomeRefreshAvailability) -> Unit,
    onCheckConnection: suspend () -> Boolean
) {
    HomeScreen(
        profile = session.profile,
        profileId = session.profileId,
        verifiedContext = session.authenticatedFeatureContext,
        availability = session.availability,
        onNavigation = navigator::handleHomeNavigation,
        onAuthenticationRejected = onAuthenticationRejected,
        onRefreshAvailabilityChanged = onRefreshAvailabilityChanged,
        onCheckConnection = onCheckConnection
    )
}

private fun AppSessionAuthority.toSettingsConnectionStatus(): SettingsConnectionStatus =
    when (this) {
        AppSessionAuthority.Restoring,
        is AppSessionAuthority.Healing,
        AppSessionAuthority.CheckingConnection -> SettingsConnectionStatus.RECONNECTING

        is AppSessionAuthority.TransientFailure,
        AppSessionAuthority.WorkingOffline -> SettingsConnectionStatus.OFFLINE

        is AppSessionAuthority.AuthenticationRequired ->
            SettingsConnectionStatus.AUTHENTICATION_REQUIRED

        is AppSessionAuthority.Verified -> SettingsConnectionStatus.CONNECTED
    }
