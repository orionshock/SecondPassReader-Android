package com.secondpasslibrary.reader.reader.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.reader.design.SecondPassTheme
import com.secondpasslibrary.reader.reader.annotations.bookmark.ReaderVisiblePageBookmarks
import com.secondpasslibrary.reader.reader.navigation.ReaderNavigationIntent
import com.secondpasslibrary.reader.reader.presentation.ReaderMarginaliaPresentationState
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ReaderBookmarkUiIntegrationTest : ReaderUiIntegrationTestSupport() {
    @Test
    fun bookmarkHudCreatesFromEmptyAndListsVisibleBookmarksWithoutImmediateDelete() {
        var creates = 0
        val navigationIntents = mutableListOf<ReaderNavigationIntent>()
        val removed = mutableListOf<String>()
        val first = testBookmark("first", "First bookmark")
        val second = testBookmark("second", "Second bookmark")
        val visibleBookmarks = mutableStateOf(ReaderVisiblePageBookmarks())
        compose.setContent {
            SecondPassTheme {
                ReaderTestScreen(
                    state = readerReadyState(),
                    onBack = {},
                    onRetry = {},
                    onCreateBookmark = {
                        creates += 1
                        visibleBookmarks.value = ReaderVisiblePageBookmarks(listOf(first))
                    },
                    onNavigationIntent = { navigationIntents += it },
                    onRemoveBookmark = {
                        removed += it.id
                        visibleBookmarks.value = ReaderVisiblePageBookmarks(
                            visibleBookmarks.value.bookmarks.filterNot { existing ->
                                existing.id == it.id
                            }
                        )
                    },
                    marginalia = ReaderMarginaliaPresentationState(
                        pageBookmarks = visibleBookmarks.value
                    )
                )
            }
        }
        compose.onNodeWithContentDescription("Add bookmark").performClick()
        compose.runOnIdle { assertEquals(1, creates) }
        compose.onNodeWithContentDescription("1 bookmark on this page").assertIsDisplayed()
        compose.onNodeWithContentDescription("1 bookmark on this page").performClick()
        compose.onNodeWithText("First bookmark").assertIsDisplayed()
        compose.onNodeWithContentDescription("Go to First bookmark").performClick()
        compose.runOnIdle {
            assertEquals(
                listOf(ReaderNavigationIntent.GoToBookmark(first)),
                navigationIntents
            )
        }

        compose.runOnIdle {
            visibleBookmarks.value = ReaderVisiblePageBookmarks(listOf(first, second))
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("2 bookmarks on this page").assertIsDisplayed()
        compose.onNodeWithContentDescription("2 bookmarks on this page").performClick()
        compose.onNodeWithText("First bookmark").assertIsDisplayed()
        compose.onNodeWithText("Second bookmark").assertIsDisplayed()
        compose.runOnIdle { assertTrue(removed.isEmpty()) }
        compose.onNodeWithContentDescription("Remove Second bookmark").performClick()
        compose.runOnIdle { assertEquals(listOf("second"), removed) }
        compose.onNodeWithContentDescription("1 bookmark on this page").assertIsDisplayed()
    }

    @Test
    fun closedSessionBookmarkHudIsReadOnly() {
        var creates = 0
        compose.setContent {
            SecondPassTheme {
                ReaderTestScreen(
                    state = readerReadyState(status = ReaderSessionStatus.CLOSED),
                    onBack = {},
                    onRetry = {},
                    onCreateBookmark = { creates += 1 }
                )
            }
        }

        compose.onNodeWithContentDescription("No bookmarks on this page, read only")
            .assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, creates) }
    }
}
