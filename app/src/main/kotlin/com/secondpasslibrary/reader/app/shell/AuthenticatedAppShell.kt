package com.secondpasslibrary.reader.app.shell

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.secondpasslibrary.client.AuthenticatedContext
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic
import com.secondpasslibrary.reader.home.AuthenticatedHome
import com.secondpasslibrary.reader.home.HomeNavigationIntent
import com.secondpasslibrary.reader.library.LibraryBooksEntry
import com.secondpasslibrary.reader.library.LibraryStateHost
import com.secondpasslibrary.reader.settings.LinkedSettings
import com.secondpasslibrary.reader.shelves.ShelvesStateHost
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AuthenticatedAppShell(
    profile: ConnectionProfile,
    context: AuthenticatedContext,
    onAuthenticationRejected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backStack = rememberNavBackStack(AppDestination.Home)
    val navigator = remember(backStack) { AppNavigator(backStack) }
    val currentDestination = backStack.lastOrNull()?.topLevelDestination() ?: AppDestination.Home
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        modifier = modifier,
        drawerContent = {
            AppDrawer(
                serverName = context.serverInfo.name.ifBlank { profile.serverName },
                selected = currentDestination,
                onSelected = { destination ->
                    navigator.select(destination)
                    coroutineScope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                if (currentDestination != AppDestination.Shelves) {
                    AppShellTopBar(currentDestination) {
                        coroutineScope.launch { drawerState.open() }
                    }
                }
            }
        ) { contentPadding ->
            AuthenticatedDestinations(
                profile,
                context,
                backStack,
                navigator,
                onAuthenticationRejected,
                onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                modifier = Modifier.fillMaxSize().padding(contentPadding)
            )
        }
    }
}

@Composable
private fun AuthenticatedDestinations(
    profile: ConnectionProfile,
    context: AuthenticatedContext,
    backStack: MutableList<NavKey>,
    navigator: AppNavigator,
    onAuthenticationRejected: () -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier
) {
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = navigator::goBack,
        entryProvider =
            entryProvider {
                entry(key = AppDestination.Home) {
                    HomeDestination(profile, context, navigator, onAuthenticationRejected)
                }
                entry(key = AppDestination.Library) {
                    LibraryDestination(
                        profile,
                        LibraryBooksEntry.Browse,
                        context.serverInfo.advancedLibraryGroupsEnabled,
                        onAuthenticationRejected
                    )
                }
                entry<LibrarySearchRoute> { route ->
                    LibraryDestination(
                        profile,
                        LibraryBooksEntry.BroadSearch(route.query),
                        context.serverInfo.advancedLibraryGroupsEnabled,
                        onAuthenticationRejected
                    )
                }
                entry(key = AppDestination.Shelves) {
                    ShelvesStateHost(profile, onOpenDrawer, onAuthenticationRejected)
                }
                entry(key = AppDestination.Sessions) {
                    DestinationPlaceholder(AppDestination.Sessions)
                }
                entry(key = AppDestination.Settings) { LinkedSettings(profile, context) }
            }
    )
}

@Composable
private fun LibraryDestination(
    profile: ConnectionProfile,
    entry: LibraryBooksEntry,
    advancedGroupsEnabled: Boolean,
    onAuthenticationRejected: () -> Unit
) {
    LibraryStateHost(profile, entry, advancedGroupsEnabled, onAuthenticationRejected)
}

@Composable
private fun HomeDestination(
    profile: ConnectionProfile,
    context: AuthenticatedContext,
    navigator: AppNavigator,
    onAuthenticationRejected: () -> Unit
) {
    AuthenticatedHome(
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
        HomeNavigationIntent.ViewAllSessions -> select(AppDestination.Sessions)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppShellTopBar(destination: AppDestination, onOpenDrawer: () -> Unit) {
    TopAppBar(
        title = { Text(destination.label) },
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                AppIconGraphic(AppIcon.NavigationMenu, "Open navigation drawer")
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            )
    )
}

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
