package com.secondpasslibrary.reader.app.shell

import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.test.ext.junit.runners.AndroidJUnit4
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
    fun inactiveEntryRetainsViewModelAndSaveableStateWhilePoppedEntryClearsViewModel() {
        lateinit var navigation: AppNavigationState
        val viewModels = mutableMapOf<NavKey, TrackingViewModel>()
        compose.setContent {
            navigation = rememberAppNavigationState()
            val provider =
                entryProvider<NavKey> {
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
        var verified by mutableStateOf(false)
        var libraryViewModel: TrackingViewModel? = null
        compose.setContent {
            navigation = rememberAppNavigationState()
            val provider =
                entryProvider<NavKey> {
                    entry(key = AppDestination.Home) { Text("Home") }
                    entry(key = AppDestination.Library) {
                        if (verified) {
                            val owner = viewModel<TrackingViewModel> { TrackingViewModel() }
                            SideEffect { libraryViewModel = owner }
                            Text("Library")
                        } else {
                            Text("Connection required")
                        }
                    }
                    entry(key = AppDestination.Shelves) { Text("Shelves") }
                    entry(key = AppDestination.Marginalia) { Text("Marginalia") }
                    entry(key = AppDestination.Settings) { Text("Settings") }
                }
            NavDisplay(
                entries = retainedActiveEntries(navigation, provider),
                onBack = { navigation.pop() }
            )
        }

        compose.runOnUiThread { navigation.select(AppDestination.Library) }
        compose.waitForIdle()
        assertTrue(libraryViewModel == null)
        assertTrue(navigation.activeBackStack.single() == AppDestination.Library)

        compose.runOnUiThread { verified = true }
        compose.waitForIdle()
        assertTrue(libraryViewModel != null)
        assertTrue(navigation.activeBackStack.single() == AppDestination.Library)
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

private class TrackingViewModel : ViewModel() {
    var cleared = false
        private set

    override fun onCleared() {
        cleared = true
    }
}
