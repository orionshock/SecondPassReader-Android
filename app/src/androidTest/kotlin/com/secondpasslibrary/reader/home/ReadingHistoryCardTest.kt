package com.secondpasslibrary.reader.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.reader.design.SecondPassTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingHistoryCardTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun primaryTapEmitsReaderIntent() {
        val intent = OpenReaderIntent("book-1", "session-1")
        val emitted = mutableListOf<OpenReaderIntent>()
        compose.setContent {
            SecondPassTheme {
                ReadingHistoryCard(
                    model = card(intent),
                    onPrimaryAction = emitted::add,
                    onContextAction = {}
                )
            }
        }

        compose
            .onNode(
                hasClickAction() and hasAnyDescendant(hasText("Test Book")),
                useUnmergedTree = true
            )
            .performClick()

        compose.runOnIdle { assertEquals(listOf(intent), emitted) }
    }

    @Test
    fun unavailableOfflineCardUsesQuietAccessibleAffordanceWithoutWarningText() {
        val emitted = mutableListOf<OpenReaderIntent>()
        compose.setContent {
            SecondPassTheme {
                ReadingHistoryCard(
                    model = card(intent = null, unavailableOffline = true),
                    onPrimaryAction = emitted::add,
                    onContextAction = {}
                )
            }
        }

        assertEquals(
            0,
            compose.onAllNodesWithText("Not available offline").fetchSemanticsNodes().size
        )
        compose.onNodeWithContentDescription(OFFLINE_UNAVAILABLE_DESCRIPTION).assertExists()
        compose
            .onNode(
                hasClickAction() and hasAnyDescendant(hasText("Test Book")),
                useUnmergedTree = true
            )
            .performClick()

        compose.runOnIdle { assertEquals(emptyList<OpenReaderIntent>(), emitted) }
    }

    @Test
    fun cardExposesSessionIdentityStatusAndProgressAsOneAccessibleDescription() {
        compose.setContent {
            SecondPassTheme {
                ReadingHistoryCard(
                    model = card(OpenReaderIntent("book-1", "session-1")),
                    onPrimaryAction = {},
                    onContextAction = {}
                )
            }
        }

        compose
            .onNodeWithContentDescription("Test Book, Evening reread, Chapter 8, Active")
            .assertContentDescriptionEquals(
                "Test Book, Evening reread, Chapter 8, Active"
            )
            .assertHasClickAction()
    }

    private fun card(intent: OpenReaderIntent?, unavailableOffline: Boolean = false) =
        ReadingHistoryCardModel(
            bookId = intent?.bookId ?: "book-1",
            sessionId = intent?.sessionId ?: "session-1",
            title = "Test Book",
            sessionIdentityLabel = "Evening reread",
            locationLabel = "Chapter 8",
            statusLabel = "Active",
            statusIndicator = ReadingStatusIndicator.Active,
            accessibilityDescription = "Test Book, Evening reread, Chapter 8, Active",
            cover = BookCoverPresentation.Missing,
            primaryIntent = intent,
            unavailableOffline = unavailableOffline,
            contextActions = emptyList()
        )
}
