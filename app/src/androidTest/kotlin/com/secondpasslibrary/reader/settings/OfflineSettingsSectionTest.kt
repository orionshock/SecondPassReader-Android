package com.secondpasslibrary.reader.settings

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.secondpasslibrary.reader.app.AppAvailability
import com.secondpasslibrary.reader.app.AppAvailabilityReason
import com.secondpasslibrary.reader.app.storage.AccountLocalDownload
import com.secondpasslibrary.reader.design.SecondPassTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineSettingsSectionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun workOfflineAndReconnectAreTheFirstConnectionControls() {
        val availability = mutableStateOf<AppAvailability>(AppAvailability.Online)
        var offlineRequests = 0
        var reconnectRequests = 0
        compose.setContent {
            SecondPassTheme {
                Column {
                    OfflineSettingsSection(
                        availability = availability.value,
                        checkingConnection = false,
                        onWorkOffline = { offlineRequests++ },
                        onReconnect = { reconnectRequests++ },
                        state = SettingsDownloadsState(loading = false),
                        onRefresh = {},
                        onRemove = {},
                        onRemoveAll = {},
                        onClearBook = {},
                        onBookDetails = {}
                    )
                }
            }
        }
        compose.onNodeWithTag("work-offline-switch").assertIsOff().performClick()
        compose.onNodeWithText("Connection").assertDoesNotExist()
        compose.onNodeWithText("Connected to Library").assertDoesNotExist()
        assertTrue(
            compose.onNodeWithTag("check-connection").getUnclippedBoundsInRoot().left <
                compose.onNodeWithTag("work-offline-switch").getUnclippedBoundsInRoot().left
        )
        compose.waitForIdle()
        assertEquals(1, offlineRequests)
        compose.runOnUiThread {
            availability.value = AppAvailability.Offline(AppAvailabilityReason.USER_CHOICE)
        }
        compose.onNodeWithTag("work-offline-switch").assertIsOn()
        compose.onNodeWithText("Working offline").assertDoesNotExist()
        compose.onNodeWithTag("check-connection").performClick()
        compose.waitUntil(5_000) { reconnectRequests == 1 }
        assertEquals(1, reconnectRequests)
    }

    @Test
    fun individualAndAllRemovalRequireConfirmation() {
        val removed = mutableListOf<String>()
        var removedAll = 0
        val cleared = mutableListOf<String>()
        val opened = mutableListOf<String>()
        compose.setContent {
            SecondPassTheme {
                Column {
                    OfflineSettingsSection(
                        availability = AppAvailability.Online,
                        checkingConnection = false,
                        onWorkOffline = {},
                        onReconnect = {},
                        state = SettingsDownloadsState(
                            downloads = listOf(
                                AccountLocalDownload(
                                    "book-1",
                                    "First",
                                    1234,
                                    epubBytes = 1234
                                ),
                                AccountLocalDownload(
                                    "book-2",
                                    "Second",
                                    5678,
                                    epubBytes = 5678
                                )
                            ),
                            loading = false
                        ),
                        onRefresh = {},
                        onRemove = { removed += it },
                        onRemoveAll = { removedAll++ },
                        onClearBook = { cleared += it },
                        onBookDetails = { opened += it }
                    )
                }
            }
        }
        compose.onNodeWithText("2 downloaded Books").assertIsDisplayed()
        compose.onNodeWithText("Manage downloads").assertDoesNotExist()
        compose.onNodeWithText("First").assertIsDisplayed()
        compose.onNodeWithText("Second").assertIsDisplayed()
        capture("list")
        compose.onNodeWithTag("download-book-1").performClick()
        compose.onNodeWithText("Downloaded EPUB", substring = true).assertIsDisplayed()
        capture("details")
        compose.onNodeWithText("Book details").performClick()
        assertEquals(listOf("book-1"), opened)
        compose.onNodeWithContentDescription("Remove download of First").performClick()
        compose.onNodeWithText("Downloaded EPUB", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Remove download?").assertIsDisplayed()
        capture("remove-confirm")
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(emptyList<String>(), removed)

        compose.onNodeWithContentDescription("Remove download of First").performClick()
        compose.onNodeWithTag("confirm-download-removal").performClick()
        assertEquals(listOf("book-1"), removed)

        compose.onNodeWithTag("download-book-2").performClick()
        compose.onNodeWithText("Clear offline data").performClick()
        compose.onNodeWithText("Clear offline data for this Book?").assertIsDisplayed()
        capture("clear-confirm")
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(emptyList<String>(), cleared)
        compose.onNodeWithTag("download-book-2").performClick()
        compose.onNodeWithText("Clear offline data").performClick()
        compose.onNodeWithTag("confirm-download-removal").performClick()
        assertEquals(listOf("book-2"), cleared)

        compose.onNodeWithText("Remove all downloads").performClick()
        compose.onNodeWithText("Remove all downloads?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, removedAll)
        compose.onNodeWithText("Remove all downloads").performClick()
        compose.onNodeWithText("Remove downloads").performClick()
        assertEquals(1, removedAll)
    }

    @Test
    fun emptyDownloadsHaveNoManagementReveal() {
        compose.setContent {
            SecondPassTheme {
                OfflineSettingsSection(
                    availability = AppAvailability.Online,
                    checkingConnection = false,
                    onWorkOffline = {},
                    onReconnect = {},
                    state = SettingsDownloadsState(loading = false),
                    onRefresh = {},
                    onRemove = {},
                    onRemoveAll = {},
                    onClearBook = {},
                    onBookDetails = {}
                )
            }
        }
        compose.onNodeWithText("No downloaded Books").assertIsDisplayed()
        compose.onNodeWithText("Manage downloads").assertDoesNotExist()
        capture("empty")
    }

    private fun capture(state: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500, 3_000)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.getExternalFilesDir(null), "settings-offline-fixtures")
        check(directory.exists() || directory.mkdirs())
        File(directory, "$state.png").outputStream().use { output ->
            check(
                InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                    .compress(Bitmap.CompressFormat.PNG, 100, output)
            )
        }
    }
}
