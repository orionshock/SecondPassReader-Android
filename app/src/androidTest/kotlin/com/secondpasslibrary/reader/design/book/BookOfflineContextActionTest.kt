package com.secondpasslibrary.reader.design.book

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.reader.design.SecondPassTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookOfflineContextActionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun contextualActionChangesWithOfflineAvailability() {
        val downloaded = mutableStateOf(false)
        val selected = mutableListOf<BookCardAction>()
        compose.setContent {
            SecondPassTheme {
                CompactBookRow(
                    book = CompactBookPresentation(
                        "book-1", "Book", null, null, null, null, null, "EPUB", null
                    ),
                    actions = listOf(
                        if (downloaded.value) {
                            BookCardAction.RemoveDownload("book-1")
                        } else {
                            BookCardAction.MakeAvailableOffline("book-1")
                        }
                    ),
                    onAction = { selected += it },
                    onClick = {}
                )
            }
        }
        compose.onNodeWithContentDescription("Book actions for Book").performClick()
        compose.onNodeWithText("Make available offline").performClick()
        assertEquals(listOf(BookCardAction.MakeAvailableOffline("book-1")), selected)

        compose.runOnUiThread { downloaded.value = true }
        compose.onNodeWithContentDescription("Book actions for Book").performClick()
        compose.onNodeWithText("Remove download").performClick()
        assertEquals(BookCardAction.RemoveDownload("book-1"), selected.last())
    }
}
