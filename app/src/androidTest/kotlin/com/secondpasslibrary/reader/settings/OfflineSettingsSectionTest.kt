package com.secondpasslibrary.reader.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.reader.app.storage.AccountLocalDownload
import com.secondpasslibrary.reader.design.SecondPassTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineSettingsSectionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun individualAndAllRemovalRequireConfirmation() {
        val removed = mutableListOf<String>()
        var removedAll = 0
        compose.setContent {
            SecondPassTheme {
                OfflineSettingsSection(
                    SettingsDownloadsState(
                        downloads = listOf(
                            AccountLocalDownload("book-1", "First", 1234),
                            AccountLocalDownload("book-2", "Second", 5678)
                        ),
                        loading = false
                    ),
                    onRefresh = {},
                    onRemove = { removed += it },
                    onRemoveAll = { removedAll++ }
                )
            }
        }
        compose.onNodeWithText("2 downloaded Books").assertIsDisplayed()
        compose.onNodeWithText("Manage downloads").performClick()
        compose.onNodeWithText("First").assertIsDisplayed()
        compose.onNodeWithText("Second").assertIsDisplayed()
        compose.onNodeWithContentDescription("Remove download of First").performClick()
        compose.onNodeWithText("Remove download?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(emptyList<String>(), removed)

        compose.onNodeWithContentDescription("Remove download of First").performClick()
        compose.onNodeWithTag("confirm-download-removal").performClick()
        assertEquals(listOf("book-1"), removed)

        compose.onNodeWithText("Remove all downloads").performClick()
        compose.onNodeWithText("Remove all downloads?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, removedAll)
        compose.onNodeWithText("Remove all downloads").performClick()
        compose.onNodeWithText("Remove downloads").performClick()
        assertEquals(1, removedAll)
    }
}
