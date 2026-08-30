package com.secondpasslibrary.reader.design.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.reader.design.SecondPassTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContextualAppBarAvailabilityTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun connectionStatusIsAmbientAndDoesNotAddLayoutBanner() {
        var status by mutableStateOf(AppBarNetworkStatus.SYNCING)
        compose.setContent {
            SecondPassTheme {
                CompositionLocalProvider(
                    LocalAppBarNetworkStatus provides AppBarNetworkPresentation(status)
                ) {
                    ContextualAppBar(
                        AppBarPresentation(AppBarNavigation.MENU, "Home"),
                        onNavigation = {}
                    )
                }
            }
        }

        compose.onNodeWithContentDescription("Connecting and refreshing").assertIsDisplayed()
        compose.onNodeWithText("Reconnecting…").assertIsNotDisplayed()

        compose.runOnUiThread { status = AppBarNetworkStatus.OFFLINE }
        compose.onNodeWithContentDescription("Offline").assertIsDisplayed()
        compose.onNodeWithText("Offline — cached Home remains available.").assertIsNotDisplayed()

        compose.runOnUiThread { status = AppBarNetworkStatus.SETTLED }
        compose.onNodeWithContentDescription("Offline").assertIsNotDisplayed()
        compose.onNodeWithContentDescription("Connecting and refreshing").assertIsNotDisplayed()
    }

    @Test
    fun offlineIndicatorRoutesItsExplicitRecoveryAction() {
        var retries = 0
        compose.setContent {
            SecondPassTheme {
                CompositionLocalProvider(
                    LocalAppBarNetworkStatus provides
                        AppBarNetworkPresentation(
                            AppBarNetworkStatus.OFFLINE,
                            "Offline — retry connection",
                            onOfflineClick = { retries += 1 }
                        )
                ) {
                    ContextualAppBar(
                        AppBarPresentation(AppBarNavigation.MENU, "Home"),
                        onNavigation = {}
                    )
                }
            }
        }

        compose.onNodeWithContentDescription("Offline — retry connection").performClick()

        compose.runOnIdle { check(retries == 1) }
    }
}
