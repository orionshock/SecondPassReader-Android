package com.secondpasslibrary.reader.app.shell

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EdgeDrawerGestureTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun edgeSwipeOpensDrawerWithoutChangingPushedRouteAndDismissKeepsRoute() {
        val navigation =
            appNavigationStateForTest(AppDestination.Library).apply {
                push(BookDetailRoute("book-1", BookDetailReturnTarget.Library))
            }
        lateinit var drawerState: DrawerState
        lateinit var coroutineScope: CoroutineScope
        compose.setContent {
            drawerState = rememberDrawerState(DrawerValue.Closed)
            coroutineScope = rememberCoroutineScope()
            DrawerGestureHarness(drawerState, navigation.currentRoute.drawerGestureEnabled)
        }

        compose.onNodeWithTag(SHELL_TAG).performTouchInput {
            val y = height * 0.15f
            down(Offset(1f, y))
            moveTo(Offset(width * 0.3f, y), 300L)
            up()
        }

        compose.onNodeWithText(DRAWER_TEXT).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(
                BookDetailRoute("book-1", BookDetailReturnTarget.Library),
                navigation.currentRoute
            )
        }

        compose.runOnUiThread { coroutineScope.launch { drawerState.close() } }
        compose.waitUntil { drawerState.currentValue == DrawerValue.Closed }
        compose.runOnIdle {
            assertEquals(
                BookDetailRoute("book-1", BookDetailReturnTarget.Library),
                navigation.currentRoute
            )
        }
    }

    @Test
    fun swipeAwayFromEdgeDoesNotOpenDrawer() {
        lateinit var drawerState: DrawerState
        compose.setContent {
            drawerState = rememberDrawerState(DrawerValue.Closed)
            DrawerGestureHarness(drawerState, enabled = true)
        }

        compose.onNodeWithTag(SHELL_TAG).performTouchInput {
            val y = height * 0.15f
            down(Offset(width * 0.35f, y))
            moveTo(Offset(width * 0.7f, y), 300L)
            up()
        }

        compose.runOnIdle { assertEquals(DrawerValue.Closed, drawerState.currentValue) }
    }
}

@androidx.compose.runtime.Composable
private fun DrawerGestureHarness(drawerState: DrawerState, enabled: Boolean) {
    val coroutineScope = rememberCoroutineScope()
    val edgeWidth = with(LocalDensity.current) { DRAWER_GESTURE_EDGE_WIDTH.toPx() }
    val edgeHeight = with(LocalDensity.current) { DRAWER_GESTURE_EDGE_HEIGHT.toPx() }
    ModalNavigationDrawer(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag(SHELL_TAG)
                .edgeDrawerGesture(enabled, edgeWidth, edgeHeight) {
                    coroutineScope.launch { drawerState.open() }
                },
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = { ModalDrawerSheet { Text(DRAWER_TEXT) } }
    ) {
        Box(Modifier.fillMaxSize()) { Text("Pushed route") }
    }
}

private const val SHELL_TAG = "account-shell"
private const val DRAWER_TEXT = "Navigation drawer"
