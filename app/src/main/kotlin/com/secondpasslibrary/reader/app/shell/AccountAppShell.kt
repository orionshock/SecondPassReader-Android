package com.secondpasslibrary.reader.app.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.app.AppAvailability
import com.secondpasslibrary.reader.app.AppAvailabilityReason
import com.secondpasslibrary.reader.app.AppSessionAuthority
import com.secondpasslibrary.reader.app.AppSessionState
import com.secondpasslibrary.reader.app.ReaderSyncOutcomeNotice
import com.secondpasslibrary.reader.app.authenticatedFeatureContext
import com.secondpasslibrary.reader.connection.ConnectionLifecycleActionState
import com.secondpasslibrary.reader.connection.ConnectionLifecycleActions
import com.secondpasslibrary.reader.connection.ConnectionScreen
import com.secondpasslibrary.reader.connection.ConnectionScreenActions
import com.secondpasslibrary.reader.connection.ConnectionUiState
import com.secondpasslibrary.reader.design.components.AppBarNetworkPresentation
import com.secondpasslibrary.reader.design.components.AppBarNetworkStatus
import com.secondpasslibrary.reader.design.components.ContextualAppBar
import com.secondpasslibrary.reader.design.components.LocalAppBarNetworkStatus
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic
import com.secondpasslibrary.reader.home.HomeRefreshAvailability
import kotlinx.coroutines.launch

@Composable
@Suppress("LongMethod") // One shell composition wires navigation and account status.
internal fun AccountAppShell(
    session: AppSessionState.AccountShell,
    connectionActions: ConnectionScreenActions,
    lifecycleActionState: ConnectionLifecycleActionState,
    lifecycleActions: ConnectionLifecycleActions,
    onAuthenticationRejected: () -> Unit,
    onHomeRefreshAvailabilityChanged: (HomeRefreshAvailability) -> Unit,
    onCheckConnection: suspend () -> Boolean,
    onWorkOffline: () -> Unit,
    onReconnect: () -> Unit,
    syncOutcomeNotice: ReaderSyncOutcomeNotice?,
    onSyncOutcomeNoticeAcknowledged: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val navigation = rememberAppNavigationState()
    val navigator = remember(navigation) { AppNavigator(navigation) }
    val currentRoute = navigation.currentRoute
    val drawer = rememberAccountDrawerState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerGestureModifier =
        Modifier.accountDrawerGestureModifier(currentRoute) {
            coroutineScope.launch { drawer.state.open() }
        }

    CompositionLocalProvider(
        LocalAppBarNetworkStatus provides
            session.availability.toAppBarNetworkPresentation(connectionActions)
    ) {
        Box(modifier) {
            ModalNavigationDrawer(
                modifier = drawerGestureModifier,
                drawerState = drawer.state,
                gesturesEnabled = drawer.gesturesEnabled,
                drawerContent = {
                    AppDrawer(
                        serverName = session.serverName,
                        selected = navigation.selectedDestination,
                        onSelected = { destination ->
                            navigator.select(destination)
                            coroutineScope.launch { drawer.state.close() }
                        }
                    )
                }
            ) {
                AccountShellScaffold(
                    session,
                    connectionActions,
                    lifecycleActionState,
                    lifecycleActions,
                    navigation,
                    navigator,
                    onAuthenticationRejected,
                    onHomeRefreshAvailabilityChanged,
                    onCheckConnection,
                    onWorkOffline,
                    onReconnect,
                    snackbarHostState,
                    onOpenDrawer = { coroutineScope.launch { drawer.state.open() } }
                )
            }
            InteractiveConnectionOverlay(session.authority, connectionActions)
        }
    }
    AppShellRootBackHandler(navigation, navigator)
    DrawerDismissBackHandler(
        enabled = drawer.gesturesEnabled,
        onDismiss = { coroutineScope.launch { drawer.state.close() } }
    )
    ReaderSyncOutcomeSnackbar(
        syncOutcomeNotice.takeUnless {
            session.authority.requiresInteractiveConnectionPresentation()
        },
        snackbarHostState,
        onSyncOutcomeNoticeAcknowledged
    )
}

@Composable
private fun AccountShellScaffold(
    session: AppSessionState.AccountShell,
    connectionActions: ConnectionScreenActions,
    lifecycleActionState: ConnectionLifecycleActionState,
    lifecycleActions: ConnectionLifecycleActions,
    navigation: AppNavigationState,
    navigator: AppNavigator,
    onAuthenticationRejected: () -> Unit,
    onHomeRefreshAvailabilityChanged: (HomeRefreshAvailability) -> Unit,
    onCheckConnection: suspend () -> Boolean,
    onWorkOffline: () -> Unit,
    onReconnect: () -> Unit,
    snackbarHostState: SnackbarHostState,
    onOpenDrawer: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (showsShellTopBar(navigation.selectedDestination, navigation.currentRoute)) {
                ContextualAppBar(
                    navigation.selectedDestination.rootAppBarPresentation(),
                    onNavigation = onOpenDrawer
                )
            }
        }
    ) { contentPadding ->
        Column(Modifier.fillMaxSize().padding(contentPadding)) {
            AccountDestinations(
                session = session,
                lifecycleActionState = lifecycleActionState,
                lifecycleActions = lifecycleActions,
                navigation = navigation,
                navigator = navigator,
                onAuthenticationRejected = onAuthenticationRejected,
                onHomeRefreshAvailabilityChanged = onHomeRefreshAvailabilityChanged,
                onCheckConnection = onCheckConnection,
                onWorkOffline = onWorkOffline,
                onReconnect = onReconnect,
                onRetryConnection = connectionActions.retryRestore,
                onRelinkAccount = connectionActions.relinkLocalAccount,
                onForgetAccount = connectionActions.forgetLocalConnection,
                onOpenDrawer = onOpenDrawer,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun AppAvailability.toAppBarNetworkPresentation(
    actions: ConnectionScreenActions
): AppBarNetworkPresentation = when (this) {
    AppAvailability.Syncing -> AppBarNetworkPresentation(AppBarNetworkStatus.SYNCING)

    AppAvailability.Online -> AppBarNetworkPresentation(AppBarNetworkStatus.SETTLED)

    is AppAvailability.Offline ->
        when (reason) {
            AppAvailabilityReason.UNREACHABLE ->
                AppBarNetworkPresentation(
                    AppBarNetworkStatus.OFFLINE,
                    "Offline — retry connection",
                    actions.retryRestore
                )

            AppAvailabilityReason.USER_CHOICE ->
                AppBarNetworkPresentation(AppBarNetworkStatus.OFFLINE, "Working offline")

            AppAvailabilityReason.AUTHENTICATION_REQUIRED ->
                AppBarNetworkPresentation(
                    AppBarNetworkStatus.OFFLINE,
                    "Connection needs repair",
                    actions.relinkLocalAccount
                )
        }
}

private data class AccountDrawerState(val state: DrawerState, val gesturesEnabled: Boolean)

@Composable
private fun rememberAccountDrawerState(): AccountDrawerState {
    var gesturesEnabled by remember { mutableStateOf(false) }
    val state =
        rememberDrawerState(DrawerValue.Closed) { target ->
            gesturesEnabled = target == DrawerValue.Open
            true
        }
    return AccountDrawerState(state, gesturesEnabled)
}

@Composable
private fun DrawerDismissBackHandler(enabled: Boolean, onDismiss: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onDismiss)
}

@Composable
private fun Modifier.accountDrawerGestureModifier(
    route: AppRoute,
    onOpenDrawer: () -> Unit
): Modifier {
    val density = LocalDensity.current
    return edgeDrawerGesture(
        enabled = route.drawerGestureEnabled,
        edgeWidth = with(density) { DRAWER_GESTURE_EDGE_WIDTH.toPx() },
        edgeHeight = with(density) { DRAWER_GESTURE_EDGE_HEIGHT.toPx() },
        onOpenDrawer = onOpenDrawer
    )
}

@Composable
private fun InteractiveConnectionOverlay(
    authority: AppSessionAuthority,
    connectionActions: ConnectionScreenActions
) {
    val healing = authority as? AppSessionAuthority.Healing ?: return
    if (!healing.connection.requiresInteractiveConnectionPresentation()) return
    ConnectionScreen(healing.connection, connectionActions)
}

internal fun AppSessionAuthority.requiresInteractiveConnectionPresentation(): Boolean =
    (this as? AppSessionAuthority.Healing)
        ?.connection
        ?.requiresInteractiveConnectionPresentation()
        ?: false

private fun ConnectionUiState.requiresInteractiveConnectionPresentation(): Boolean = when (this) {
    is ConnectionUiState.ServerConfirmed,
    is ConnectionUiState.StartingPairing,
    is ConnectionUiState.WaitingForApproval,
    is ConnectionUiState.CompletingPairing,
    is ConnectionUiState.PersistenceRecovery,
    is ConnectionUiState.StoredCredentialProblem,
    is ConnectionUiState.TerminalPairingProblem -> true

    else -> false
}

internal fun showsShellTopBar(destination: AppDestination, route: AppRoute): Boolean =
    destination != AppDestination.Library &&
        destination != AppDestination.Shelves &&
        destination != AppDestination.Marginalia &&
        route !is BookDetailRoute &&
        route !is ReaderRoute &&
        route !is BookMarginaliaRoute &&
        route !is ReadingSessionDetailRoute

private val AppSessionState.AccountShell.serverName: String
    get() =
        authenticatedFeatureContext
            ?.serverInfo?.name
            ?.ifBlank { profile.serverName }
            ?: profile.serverName

@Composable
private fun AppDrawer(
    serverName: String,
    selected: AppDestination,
    onSelected: (AppDestination) -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.widthIn(max = 320.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Text(
            "SECOND PASS",
            modifier = Modifier.padding(start = 20.dp, top = 24.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge
        )
        Text(
            serverName,
            modifier = Modifier.padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        AppDestination.entries.forEach { destination ->
            NavigationDrawerItem(
                label = { Text(destination.label) },
                selected = destination == selected,
                onClick = { onSelected(destination) },
                icon = { AppIconGraphic(destination.icon, null) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }
    }
}
