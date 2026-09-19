package com.secondpasslibrary.reader.app.shell

import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.client.AuthenticatedContext
import com.secondpasslibrary.client.AuthenticatedServerInfo
import com.secondpasslibrary.client.CurrentUser
import com.secondpasslibrary.reader.app.AppSessionAuthority
import com.secondpasslibrary.reader.app.AppSessionState
import com.secondpasslibrary.reader.bookdetail.BookDetailNavigationIntent
import com.secondpasslibrary.reader.connection.ConnectionLifecycleActionState
import com.secondpasslibrary.reader.connection.ConnectionLifecycleActions
import com.secondpasslibrary.reader.connection.ConnectionProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNavigationEntryLifecycleTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun systemBackPopsNestedLibraryThenReturnsNonHomeRootToHome() {
        lateinit var navigation: AppNavigationState
        lateinit var navigator: AppNavigator
        compose.setContent {
            navigation = rememberAppNavigationState()
            navigator = remember(navigation) { AppNavigator(navigation) }
            val provider = entryProvider<AppRoute> {
                AppDestination.entries.forEach { destination ->
                    entry(key = destination) { Text(destination.label) }
                }
                entry<BookDetailRoute> { route -> Text("Book ${route.bookId}") }
                entry<LibraryTagRoute> { route -> Text("Tag ${route.tagId}") }
            }
            NavDisplay(
                entries = retainedActiveEntries(navigation, provider),
                onBack = { navigator.goBack() }
            )
            AppShellRootBackHandler(navigation, navigator)
        }

        val detail = BookDetailRoute("book-1", BookDetailReturnTarget.Library)
        compose.runOnUiThread {
            navigator.select(AppDestination.Library)
            navigation.push(detail)
            navigator.handleBookDetailNavigation(
                BookDetailNavigationIntent.Tag("tag-1", "fiction"),
                detail
            )
        }
        compose.onNodeWithText("Tag tag-1").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Book book-1").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Library").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Home").assertIsDisplayed()
        assertEquals(AppDestination.Home, navigation.selectedDestination)
    }

    @Test
    fun systemBackFromCrossRootFilterRestoresOriginDetail() {
        lateinit var navigation: AppNavigationState
        lateinit var navigator: AppNavigator
        compose.setContent {
            navigation = rememberAppNavigationState()
            navigator = remember(navigation) { AppNavigator(navigation) }
            val provider = entryProvider<AppRoute> {
                AppDestination.entries.forEach { destination ->
                    entry(key = destination) { Text(destination.label) }
                }
                entry<BookDetailRoute> { route -> Text("Book ${route.bookId}") }
                entry<LibraryAuthorRoute> { route -> Text("Author ${route.authorId}") }
            }
            NavDisplay(
                entries = retainedActiveEntries(navigation, provider),
                onBack = { navigator.goBack() }
            )
            AppShellRootBackHandler(navigation, navigator)
        }

        val detail = BookDetailRoute("book-1", BookDetailReturnTarget.Home)
        compose.runOnUiThread {
            navigation.push(detail)
            navigator.handleBookDetailNavigation(
                BookDetailNavigationIntent.Author("author-1"),
                detail
            )
        }
        compose.onNodeWithText("Author author-1").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Book book-1").assertIsDisplayed()
        assertEquals(AppDestination.Home, navigation.selectedDestination)
        assertEquals(detail, navigation.currentRoute)
    }

    @Test
    fun systemBackFromShelvesMarginaliaAndSettingsRootsReturnsHome() {
        lateinit var navigation: AppNavigationState
        lateinit var navigator: AppNavigator
        compose.setContent {
            navigation = rememberAppNavigationState()
            navigator = remember(navigation) { AppNavigator(navigation) }
            val provider = entryProvider<AppRoute> {
                AppDestination.entries.forEach { destination ->
                    entry(key = destination) { Text(destination.label) }
                }
            }
            NavDisplay(
                entries = retainedActiveEntries(navigation, provider),
                onBack = { navigator.goBack() }
            )
            AppShellRootBackHandler(navigation, navigator)
        }

        listOf(AppDestination.Shelves, AppDestination.Marginalia, AppDestination.Settings)
            .forEach { destination ->
                compose.runOnUiThread { navigator.select(destination) }
                compose.onNodeWithText(destination.label).assertIsDisplayed()
                compose.runOnUiThread {
                    compose.activity.onBackPressedDispatcher.onBackPressed()
                }
                compose.onNodeWithText("Home").assertIsDisplayed()
                assertEquals(AppDestination.Home, navigation.selectedDestination)
            }
    }

    @Test
    fun inactiveEntryRetainsViewModelAndSaveableStateWhilePoppedEntryClearsViewModel() {
        lateinit var navigation: AppNavigationState
        val viewModels = mutableMapOf<AppRoute, TrackingViewModel>()
        compose.setContent {
            navigation = rememberAppNavigationState()
            val provider =
                entryProvider<AppRoute> {
                    AppDestination.entries.forEach { destination ->
                        entry(key = destination) {
                            val owner = viewModel<TrackingViewModel> { TrackingViewModel() }
                            SideEffect { viewModels[destination] = owner }
                            var count by rememberSaveable { mutableStateOf(0) }
                            Button(
                                onClick = { count += 1 },
                                modifier = Modifier.testTag(destination.name)
                            ) {
                                Text(count.toString())
                            }
                        }
                    }
                    entry<BookDetailRoute> { route ->
                        val owner = viewModel<TrackingViewModel> { TrackingViewModel() }
                        SideEffect { viewModels[route] = owner }
                        Text(route.bookId)
                    }
                }
            NavDisplay(
                entries = retainedActiveEntries(navigation, provider),
                onBack = { navigation.pop() }
            )
        }

        compose.onNodeWithTag(AppDestination.Home.name).performClick()
        val homeViewModel = viewModels.getValue(AppDestination.Home)
        compose.runOnUiThread { navigation.select(AppDestination.Library) }
        compose.waitForIdle()
        val libraryViewModel = viewModels.getValue(AppDestination.Library)

        compose.runOnUiThread { navigation.select(AppDestination.Home) }
        compose.onNodeWithTag(AppDestination.Home.name).assertTextEquals("1")
        assertSame(homeViewModel, viewModels.getValue(AppDestination.Home))
        assertFalse(homeViewModel.cleared)
        assertFalse(libraryViewModel.cleared)

        val detail = BookDetailRoute("book-1", BookDetailReturnTarget.Library)
        compose.runOnUiThread {
            navigation.select(AppDestination.Library)
            navigation.push(detail)
        }
        compose.waitForIdle()
        val detailViewModel = viewModels.getValue(detail)
        compose.runOnUiThread { navigation.pop() }
        compose.waitForIdle()

        assertTrue(detailViewModel.cleared)
        assertFalse(libraryViewModel.cleared)
    }

    @Test
    fun unavailableEntryDoesNotCreateViewModelAndVerifiedTransitionKeepsRoute() {
        lateinit var navigation: AppNavigationState
        var session by mutableStateOf(accountShell(AppSessionAuthority.Restoring))
        var libraryViewModel: TrackingViewModel? = null
        compose.setContent {
            navigation = rememberAppNavigationState()
            val environment =
                rememberUpdatedState(
                    AccountDestinationEnvironment(
                        session = session,
                        lifecycleActionState = ConnectionLifecycleActionState.Idle,
                        lifecycleActions =
                            ConnectionLifecycleActions(
                                reconnect = {},
                                retryConnection = {},
                                logout = {},
                                forget = {}
                            ),
                        navigator = AppNavigator(navigation),
                        onAuthenticationRejected = {},
                        onHomeRefreshAvailabilityChanged = {},
                        onCheckConnection = { true },
                        onWorkOffline = {},
                        onReconnect = {},
                        onRetryConnection = {},
                        onRelinkAccount = {},
                        onForgetAccount = {},
                        onOpenDrawer = {}
                    )
                )
            val provider =
                entryProvider<AppRoute> {
                    entry(key = AppDestination.Home) { Text("Home") }
                    entry(key = AppDestination.Library) {
                        AuthenticatedDestination(environment) {
                            val owner = viewModel<TrackingViewModel> { TrackingViewModel() }
                            SideEffect { libraryViewModel = owner }
                            Text("Verified Library")
                        }
                    }
                    listOf(
                        AppDestination.Shelves,
                        AppDestination.Marginalia,
                        AppDestination.Settings
                    ).forEach { destination ->
                        entry(key = destination) {
                            AuthenticatedDestination(environment) {
                                Text("Verified ${destination.label}")
                            }
                        }
                    }
                }
            NavDisplay(
                entries = retainedActiveEntries(navigation, provider),
                onBack = { navigation.pop() }
            )
        }

        compose.runOnUiThread { navigation.select(AppDestination.Library) }
        compose.onNodeWithText("Reconnecting...").assertIsDisplayed()
        assertTrue(libraryViewModel == null)
        assertTrue(navigation.activeBackStack.single() == AppDestination.Library)
        val retainedStack = navigation.activeBackStack

        compose.runOnUiThread {
            session = accountShell(AppSessionAuthority.Verified(authenticatedContext()))
        }
        compose.onNodeWithText("Verified Library").assertIsDisplayed()
        val verifiedViewModel = checkNotNull(libraryViewModel)
        assertSame(retainedStack, navigation.activeBackStack)
        assertEquals(listOf(AppDestination.Library), navigation.activeBackStack)

        listOf(AppDestination.Shelves, AppDestination.Marginalia, AppDestination.Settings)
            .forEach { destination ->
                compose.runOnUiThread { navigation.select(destination) }
                compose.onNodeWithText("Verified ${destination.label}").assertIsDisplayed()
                assertEquals(listOf(destination), navigation.activeBackStack)
            }
        compose.runOnUiThread { navigation.select(AppDestination.Library) }
        compose.onNodeWithText("Verified Library").assertIsDisplayed()

        compose.runOnUiThread {
            session = accountShell(AppSessionAuthority.TransientFailure("Offline"))
        }
        compose.onNodeWithText("This section needs a connection.").assertIsDisplayed()
        assertFalse(verifiedViewModel.cleared)

        compose.runOnUiThread {
            session = accountShell(AppSessionAuthority.Verified(authenticatedContext()))
        }
        compose.onNodeWithText("Verified Library").assertIsDisplayed()
        assertSame(verifiedViewModel, libraryViewModel)
        assertSame(retainedStack, navigation.activeBackStack)

        compose.runOnUiThread { navigation.select(AppDestination.Home) }
        compose.onNodeWithText("Home").assertIsDisplayed()
        compose.runOnUiThread { navigation.select(AppDestination.Library) }
        compose.onNodeWithText("Verified Library").assertIsDisplayed()
        assertSame(verifiedViewModel, libraryViewModel)
    }

    @Test
    fun authorityChangeRetainsNavigationWhileAccountChangeReplacesIt() {
        lateinit var navigation: AppNavigationState
        var account by mutableStateOf("account-a")
        var authority by mutableStateOf("restoring")
        compose.setContent {
            key(account) {
                navigation = rememberAppNavigationState()
                Text(authority)
            }
        }

        compose.runOnUiThread {
            navigation.select(AppDestination.Library)
            navigation.push(LibrarySearchRoute("retained query"))
        }
        compose.waitForIdle()
        val originalNavigation = navigation

        compose.runOnUiThread { authority = "verified" }
        compose.runOnIdle {
            assertSame(originalNavigation, navigation)
            assertTrue(navigation.currentRoute == LibrarySearchRoute("retained query"))
        }

        compose.runOnUiThread { account = "account-b" }
        compose.runOnIdle {
            assertNotSame(originalNavigation, navigation)
            assertTrue(navigation.selectedDestination == AppDestination.Home)
            AppDestination.entries.forEach { destination ->
                assertEquals(
                    "Unexpected retained route in $destination stack",
                    listOf(destination),
                    navigation.backStack(destination)
                )
            }
        }
    }
}

private fun accountShell(authority: AppSessionAuthority) =
    AppSessionState.AccountShell(connectionProfile(), "profile-1", authority)

private fun connectionProfile() = ConnectionProfile(
    serverOrigin = "https://library.example",
    serverBaseUrl = "https://library.example/",
    apiBaseUrl = "https://library.example/api/v1/",
    serverName = "Library",
    serverDescription = "",
    serverVersion = "1.0",
    serverReleaseDate = "2026-08-23",
    clientSessionId = "client-session-1",
    clientName = "Reader",
    clientType = "android"
)

private fun authenticatedContext() = AuthenticatedContext(
    CurrentUser("reader", "", "", "", "profile-1", "reader", emptyList(), null, null, null),
    AuthenticatedServerInfo("Library", "", "", false, null, "", null, "1.0", "")
)

private class TrackingViewModel : ViewModel() {
    var cleared = false
        private set

    override fun onCleared() {
        cleared = true
    }
}
