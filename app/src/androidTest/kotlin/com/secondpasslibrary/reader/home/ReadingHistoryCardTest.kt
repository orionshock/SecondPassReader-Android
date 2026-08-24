package com.secondpasslibrary.reader.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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
        val intent = OpenReaderIntent("book-1", "session-1", progress = null)
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

    private fun card(intent: OpenReaderIntent) = ReadingHistoryCardModel(
        bookId = intent.bookId,
        sessionId = intent.sessionId,
        title = "Test Book",
        sessionName = null,
        locationLabel = null,
        statusLabel = "Active",
        statusIndicator = ReadingStatusIndicator.Active,
        cover = BookCoverPresentation.Missing,
        primaryIntent = intent,
        contextActions = emptyList()
    )
}
