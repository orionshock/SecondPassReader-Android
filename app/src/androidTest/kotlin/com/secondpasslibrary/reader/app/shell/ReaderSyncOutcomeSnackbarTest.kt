package com.secondpasslibrary.reader.app.shell

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.secondpasslibrary.reader.app.ReaderSyncOutcomeNotice
import com.secondpasslibrary.reader.design.SecondPassTheme
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReaderSyncOutcomeSnackbarTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun combinedNoticeIsNonBlockingAndAcknowledgedAfterDismissal() {
        val acknowledged = AtomicLong()
        compose.setContent {
            SecondPassTheme {
                val host = remember { SnackbarHostState() }
                Scaffold(snackbarHost = { SnackbarHost(host) }) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        Text("Reader passage remains visible")
                    }
                }
                ReaderSyncOutcomeSnackbar(
                    ReaderSyncOutcomeNotice(7, 1, 1, 1),
                    host,
                    acknowledged::set
                )
            }
        }

        compose.onNodeWithText("Reader passage remains visible").assertIsDisplayed()
        compose.onNodeWithText("Dismiss").performClick()
        compose.waitForIdle()

        assertEquals(7, acknowledged.get())
    }
}
