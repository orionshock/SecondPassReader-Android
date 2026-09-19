package com.secondpasslibrary.reader.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.reader.design.SecondPassTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeRefreshBoxTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun pullFromTopRunsOneRefreshAndAllowsAnotherAfterCompletion() {
        var refreshes = 0
        val releaseFirst = CompletableDeferred<Unit>()
        compose.setContent {
            SecondPassTheme {
                HomeRefreshBox(onRefresh = {
                    refreshes++
                    if (refreshes == 1) releaseFirst.await()
                }) {
                    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
                }
            }
        }

        compose.onNodeWithTag("home-refresh").performTouchInput { swipeDown() }
        compose.waitUntil(5_000) { refreshes == 1 }
        compose.onNodeWithTag("home-refresh").performTouchInput { swipeDown() }
        assertEquals(1, refreshes)
        releaseFirst.complete(Unit)
        compose.waitForIdle()
        compose.onNodeWithTag("home-refresh").performTouchInput { swipeDown() }
        compose.waitUntil(5_000) { refreshes == 2 }
    }
}
