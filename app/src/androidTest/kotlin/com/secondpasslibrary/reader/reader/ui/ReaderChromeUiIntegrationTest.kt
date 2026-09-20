package com.secondpasslibrary.reader.reader.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.reader.design.SecondPassTheme
import com.secondpasslibrary.reader.reader.appearance.ReaderLayoutMode
import com.secondpasslibrary.reader.reader.appearance.ReaderTheme
import com.secondpasslibrary.reader.reader.domain.ReaderReadingStatus
import com.secondpasslibrary.reader.reader.domain.ReaderReadingStatusScope
import com.secondpasslibrary.reader.reader.navigation.ReaderNavigationIntent
import com.secondpasslibrary.reader.reader.toc.READER_TOC_BODY_TAG
import com.secondpasslibrary.reader.reader.toc.READER_TOC_CLOSE_BOOK_TAG
import com.secondpasslibrary.reader.reader.toc.READER_TOC_CLOSE_TAG
import com.secondpasslibrary.reader.reader.toc.READER_TOC_EYEBROW_TAG
import com.secondpasslibrary.reader.reader.toc.READER_TOC_FOOTER_TAG
import com.secondpasslibrary.reader.reader.toc.READER_TOC_HEADER_TAG
import com.secondpasslibrary.reader.reader.toc.READER_TOC_TITLE_TAG
import com.secondpasslibrary.reader.reader.toc.ReaderPublicationTarget
import com.secondpasslibrary.reader.reader.toc.ReaderTocEntry
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ReaderChromeUiIntegrationTest : ReaderUiIntegrationTestSupport() {
    @Test
    fun navigationFailureIsTransientAndKeepsPublicationVisible() {
        val failures = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        compose.setContent {
            SecondPassTheme {
                ReaderTestScreen(
                    state = readerReadyState(),
                    navigationFailures = failures,
                    onBack = {},
                    onRetry = {}
                )
            }
        }

        compose.runOnIdle { check(failures.tryEmit(Unit)) }
        compose.onNodeWithText(READER_NAVIGATION_FAILURE_MESSAGE).assertIsDisplayed()
        compose.onNodeWithTag(TEST_PUBLICATION_CONTENT_TAG).assertIsDisplayed()

        compose.mainClock.advanceTimeBy(10_000)
        compose.waitForIdle()
        compose.onAllNodesWithText(READER_NAVIGATION_FAILURE_MESSAGE).assertCountEquals(0)
    }

    @Test
    fun readerChromeUsesSeparatedFloatingClustersAndProtectsPublicationTop() {
        compose.setContent {
            SecondPassTheme {
                ReaderTestScreen(readerReadyState())
            }
        }

        val positioner = compose.onNodeWithTag(READER_CHROME_POSITIONER_TAG)
            .getUnclippedBoundsInRoot()
        val left = compose.onNodeWithTag(READER_CHROME_LEFT_CLUSTER_TAG)
            .getUnclippedBoundsInRoot()
        val right = compose.onNodeWithTag(READER_CHROME_RIGHT_CLUSTER_TAG)
            .getUnclippedBoundsInRoot()
        val publication = compose.onNodeWithTag(TEST_PUBLICATION_CONTENT_TAG)
            .getUnclippedBoundsInRoot()

        assertTrue(left.right - left.left < positioner.right - positioner.left)
        assertTrue(right.right - right.left < positioner.right - positioner.left)
        assertTrue(left.right < right.left)
        assertTrue(publication.top >= left.bottom)
        assertTrue(publication.top >= right.bottom)
        compose.onAllNodesWithText("A deliberately long Reader title that remains one line")[0]
            .assertIsDisplayed()
    }

    @Test
    fun readerHudShowsAmbientStatusAutoHidesAndReturnsOnPublicationTap() {
        val hud = RecordingReaderHudEvents(
            ReaderReadingStatus(8, ReaderReadingStatusScope.SECTION)
        )
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SecondPassTheme {
                ReaderTestScreen(
                    readerReadyState(hudEvents = hud),
                    onBack = {},
                    onRetry = {}
                )
            }
        }

        compose.onNodeWithTag(com.secondpasslibrary.reader.reader.ui.hud.READER_HUD_CLOCK_TAG)
            .assertExists()
        compose.onNodeWithTag(com.secondpasslibrary.reader.reader.ui.hud.READER_HUD_STATUS_TAG)
            .assertExists()
        compose.onNodeWithTag(READER_CHROME_LEFT_CLUSTER_TAG).assertIsDisplayed()

        compose.mainClock.advanceTimeBy(3_500)
        compose.onNodeWithTag(READER_CHROME_LEFT_CLUSTER_TAG).assertIsNotDisplayed()
        compose.runOnIdle { hud.tap() }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag(READER_CHROME_LEFT_CLUSTER_TAG).assertIsDisplayed()
        compose.runOnIdle { hud.tap() }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag(READER_CHROME_LEFT_CLUSTER_TAG).assertIsNotDisplayed()
    }

    @Test
    fun readerMenuOwnsNestedTocNavigationDismissalAndCloseBook() {
        val toc = RecordingReaderToc()
        val navigationIntents = mutableListOf<ReaderNavigationIntent>()
        var exits = 0
        compose.setContent {
            SecondPassTheme {
                ReaderTestScreen(
                    readerReadyState(toc),
                    onBack = { exits += 1 },
                    onRetry = {},
                    onNavigationIntent = { navigationIntents += it }
                )
            }
        }

        assertEquals(
            0,
            compose.onAllNodesWithContentDescription("Open navigation drawer")
                .fetchSemanticsNodes().size
        )
        compose.onNodeWithTag(READER_CHROME_LEFT_CLUSTER_TAG).performClick()
        compose.onNodeWithText("Part One").assertIsDisplayed()
        compose.onNodeWithText("Part One").assertIsSelected()
        val eyebrow = compose.onNodeWithTag(READER_TOC_EYEBROW_TAG).getUnclippedBoundsInRoot()
        val title = compose.onNodeWithTag(READER_TOC_TITLE_TAG).getUnclippedBoundsInRoot()
        assertTrue(eyebrow.bottom <= title.top)
        compose.onNodeWithTag(READER_TOC_CLOSE_TAG).assertIsDisplayed()
        compose.onNodeWithTag(READER_TOC_CLOSE_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Part One").assertIsNotDisplayed()

        compose.onNodeWithTag(READER_CHROME_LEFT_CLUSTER_TAG).performTouchInput {
            down(Offset(width - 12f, height / 2f))
            up()
        }
        compose.onNodeWithContentDescription("Open Chapter Two").performClick()
        compose.waitForIdle()
        assertEquals(
            listOf(ReaderNavigationIntent.GoToPublicationTarget(TEST_CHAPTER_TWO)),
            navigationIntents
        )
        assertTrue(toc.destinations.isEmpty())

        compose.onNodeWithTag(READER_CHROME_LEFT_CLUSTER_TAG).performTouchInput {
            down(Offset(width * 0.1f, height / 2f))
            up()
        }
        compose.onNodeWithText("Part One").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(0, exits)

        compose.onNodeWithTag(READER_CHROME_LEFT_CLUSTER_TAG).performClick()
        compose.onRoot().performTouchInput {
            down(Offset(width - 4f, height / 2f))
            up()
        }
        compose.waitForIdle()
        compose.onNodeWithText("Part One").assertIsNotDisplayed()
        assertEquals(0, exits)

        compose.onNodeWithTag(READER_CHROME_LEFT_CLUSTER_TAG).performClick()
        compose.onNodeWithTag(READER_TOC_CLOSE_BOOK_TAG).performClick()
        compose.runOnIdle { assertEquals(1, exits) }
    }

    @Test
    fun longTocKeepsHeaderAndExitFooterFixedWhileBodyScrolls() {
        val entries = (1..60).map { chapter ->
            ReaderTocEntry(
                title = "Chapter $chapter",
                target = ReaderPublicationTarget("text/chapter-$chapter.xhtml")
            )
        }
        compose.setContent {
            SecondPassTheme {
                ReaderTestScreen(
                    readerReadyState(RecordingReaderToc(entries, resource = null)),
                    onBack = {},
                    onRetry = {}
                )
            }
        }

        compose.onNodeWithTag(READER_CHROME_LEFT_CLUSTER_TAG).performClick()
        val headerBefore = compose.onNodeWithTag(READER_TOC_HEADER_TAG).getUnclippedBoundsInRoot()
        val footerBefore = compose.onNodeWithTag(READER_TOC_FOOTER_TAG).getUnclippedBoundsInRoot()
        compose.onNodeWithTag(READER_TOC_BODY_TAG).performScrollToNode(hasText("Chapter 60"))
        compose.onNodeWithText("Chapter 60").assertIsDisplayed().assertIsNotSelected()
        compose.onNodeWithTag(READER_TOC_CLOSE_BOOK_TAG).assertIsDisplayed()

        assertEquals(
            headerBefore,
            compose.onNodeWithTag(READER_TOC_HEADER_TAG).getUnclippedBoundsInRoot()
        )
        assertEquals(
            footerBefore,
            compose.onNodeWithTag(READER_TOC_FOOTER_TAG).getUnclippedBoundsInRoot()
        )
    }

    @Test
    fun appearancePanelUpdatesAppOwnedAppearanceAndLeavesReaderOpen() {
        val appearance = RecordingReaderAppearance()
        compose.setContent {
            SecondPassTheme {
                ReaderTestScreen(
                    readerReadyState(RecordingReaderToc(), appearance),
                    onBack = {},
                    onRetry = {},
                    onAppearanceChanged = appearance::record
                )
            }
        }

        compose.onNodeWithContentDescription("Open reading appearance").performClick()
        compose.onNodeWithText("Reading appearance").assertIsDisplayed()
        compose.onNodeWithText("Sepia").performClick()
        compose.waitUntil { appearance.appearance.value.theme == ReaderTheme.SEPIA }
        compose.onNodeWithText("Two-column").performClick()
        compose.waitUntil {
            appearance.appearance.value.layoutMode == ReaderLayoutMode.TWO_COLUMN
        }
        compose.onNodeWithContentDescription("Increase Font size").performClick()
        compose.waitUntil { appearance.appearance.value.fontScale > 1.0 }
        compose.onNodeWithContentDescription("Increase Line height").performClick()
        compose.waitUntil { appearance.appearance.value.lineHeight > 1.4 }
        compose.onNodeWithContentDescription("Publisher styles").performClick()
        compose.waitUntil { appearance.appearance.value.publisherStylesEnabled }

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodesWithText("Theme").fetchSemanticsNodes().size)
        compose.onNodeWithTag(READER_CHROME_LEFT_CLUSTER_TAG).assertIsDisplayed()
    }
}
