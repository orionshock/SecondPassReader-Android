package com.secondpasslibrary.reader.reader

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.reader.design.SecondPassTheme
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiFailure
import com.secondpasslibrary.reader.reader.cfi.EpubCfiNavigator
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import com.secondpasslibrary.reader.reader.cfi.EpubCfiReadiness
import com.secondpasslibrary.reader.reader.cfi.EpubCfiResolution
import com.secondpasslibrary.reader.reader.cfi.EpubCfiSelection
import com.secondpasslibrary.reader.reader.domain.ReaderAppearance
import com.secondpasslibrary.reader.reader.domain.ReaderAppearanceController
import com.secondpasslibrary.reader.reader.domain.ReaderEngine
import com.secondpasslibrary.reader.reader.domain.ReaderPublicationNavigationResult
import com.secondpasslibrary.reader.reader.domain.ReaderPublicationTarget
import com.secondpasslibrary.reader.reader.domain.ReaderTableOfContents
import com.secondpasslibrary.reader.reader.domain.ReaderTheme
import com.secondpasslibrary.reader.reader.domain.ReaderTocEntry
import com.secondpasslibrary.reader.reader.domain.ReaderViewport
import com.secondpasslibrary.reader.reader.domain.ReaderViewportMovements
import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderChromeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun readerMenuOwnsNestedTocNavigationBackAndReturn() {
        val toc = RecordingToc()
        var exits = 0
        compose.setContent {
            SecondPassTheme {
                ReaderScreen(readyState(toc), onBack = { exits += 1 }, onRetry = {})
            }
        }

        assertEquals(
            0,
            compose.onAllNodesWithContentDescription("Open navigation drawer")
                .fetchSemanticsNodes().size
        )
        compose.onNodeWithContentDescription("Reader menu").performClick()
        compose.onNodeWithText("Part One").assertIsDisplayed()
        compose.onNodeWithContentDescription("Open Chapter Two").performClick()
        compose.waitForIdle()
        assertEquals(listOf(CHAPTER_TWO), toc.destinations)

        compose.onNodeWithContentDescription("Reader menu").performClick()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(0, exits)

        compose.onNodeWithContentDescription("Reader menu").performClick()
        compose.onNodeWithText("Return to Book").performClick()
        compose.runOnIdle { assertEquals(1, exits) }
    }

    @Test
    fun appearancePanelUpdatesAppOwnedAppearanceAndLeavesReaderOpen() {
        val appearance = RecordingAppearance()
        compose.setContent {
            SecondPassTheme {
                ReaderScreen(
                    readyState(RecordingToc(), appearance),
                    onBack = {},
                    onRetry = {}
                )
            }
        }

        compose.onNodeWithContentDescription("Reading appearance").performClick()
        compose.onNodeWithText("Reading appearance").assertIsDisplayed()
        compose.onNodeWithText("Sepia").performClick()
        compose.waitUntil { appearance.appearance.value.theme == ReaderTheme.SEPIA }
        compose.onNodeWithContentDescription("Increase Font size").performClick()
        compose.waitUntil { appearance.appearance.value.fontScale > 1.0 }
        compose.onNodeWithContentDescription("Increase Line height").performClick()
        compose.waitUntil { appearance.appearance.value.lineHeight > 1.4 }
        compose.onNodeWithContentDescription("Publisher styles").performClick()
        compose.waitUntil { appearance.appearance.value.publisherStylesEnabled }

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodesWithText("Theme").fetchSemanticsNodes().size)
        compose.onNodeWithContentDescription("Reader menu").assertIsDisplayed()
    }

    private fun readyState(
        toc: ReaderTableOfContents,
        appearance: ReaderAppearanceController = RecordingAppearance()
    ) = ReaderState.Ready(
        title = BOOK_TITLE,
        engine = FakeEngine(toc, appearance),
        session = ReaderSessionContext("session-1", ReaderSessionStatus.ACTIVE, null),
        restore = ReaderProgressRestore.NOT_NEEDED
    )

    private class RecordingToc : ReaderTableOfContents {
        override val entries = listOf(
            ReaderTocEntry(
                title = "Part One",
                target = CHAPTER_ONE,
                children = listOf(ReaderTocEntry("Chapter Two", CHAPTER_TWO))
            )
        )
        val destinations = mutableListOf<ReaderPublicationTarget>()

        override suspend fun goTo(
            target: ReaderPublicationTarget
        ): ReaderPublicationNavigationResult {
            destinations += target
            return ReaderPublicationNavigationResult.UNAVAILABLE
        }
    }

    private class FakeEngine(
        override val tableOfContents: ReaderTableOfContents,
        override val appearance: ReaderAppearanceController
    ) : ReaderEngine {
        override val viewport = ReaderViewport { Box {} }
        override val cfiNavigator = UnusedCfiNavigator
        override val viewportMovements = ReaderViewportMovements { emptyFlow() }
        override fun close() = Unit
    }

    private class RecordingAppearance : ReaderAppearanceController {
        private val mutableAppearance = MutableStateFlow(ReaderAppearance())
        override val appearance = mutableAppearance

        override suspend fun update(appearance: ReaderAppearance) {
            mutableAppearance.value = appearance
        }
    }

    private data object UnusedCfiNavigator : EpubCfiNavigator {
        override val readiness = MutableStateFlow<EpubCfiReadiness>(EpubCfiReadiness.Available)
        override suspend fun goTo(cfi: EpubCfi) = unavailable<Unit>()
        override suspend fun currentPosition() = unavailable<EpubCfi>()
        override suspend fun currentSelection() = unavailable<EpubCfiSelection?>()
        override suspend fun resolve(cfi: EpubCfi) = unavailable<EpubCfiResolution>()
    }

    private companion object {
        const val BOOK_TITLE = "A deliberately long Reader title that remains one line"
        val CHAPTER_ONE = ReaderPublicationTarget("text/chapter-1.xhtml")
        val CHAPTER_TWO = ReaderPublicationTarget("text/chapter-2.xhtml#section")
    }
}

private fun <T> unavailable(): EpubCfiOutcome<T> =
    EpubCfiOutcome.Failure(EpubCfiFailure.NAVIGATOR_UNAVAILABLE)
