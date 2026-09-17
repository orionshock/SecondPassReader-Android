package com.secondpasslibrary.reader.reader.readium.cfi

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecorationGroupId
import com.secondpasslibrary.reader.reader.annotations.selection.ReaderSelectionController
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearance
import com.secondpasslibrary.reader.reader.appearance.ReaderTheme
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ReadiumCfiDecorationIntegrationTest : ReadiumEpubCfiNavigatorTestSupport() {
    @Test
    fun annotationDecorationsIsolateFailuresAndReapplyAfterNavigatorRecreation() = withFixture(
        "annotation-decorations.epub"
    ) { fixture ->
        launchHost(fixture).use { scenario ->
            val host = scenario.awaitReadyHost()
            runBlocking {
                host.engine.annotationDecorations.replace(
                    ReaderAnnotationDecorationGroupId.Current,
                    listOf(
                        annotationDecoration("valid", EpubCfi(CROSS_MARKUP_RANGE_CFI)),
                        annotationDecoration("cross-spine", EpubCfi(CROSS_SPINE_RANGE_CFI)),
                        annotationDecoration("invalid", EpubCfi("epubcfi(not-valid)"))
                    )
                )
                awaitDecoration(host.navigator)
                withTimeout(HOST_TIMEOUT_MILLIS) {
                    host.engine.annotationDecorations.failures.first { "invalid" in it }
                }
                host.engine.appearance.update(ReaderAppearance(theme = ReaderTheme.DARK))
                awaitDecoration(host.navigator)
                host.engine.appearance.update(ReaderAppearance(theme = ReaderTheme.LIGHT))
                awaitDecoration(host.navigator)
                host.engine.appearance.update(ReaderAppearance(theme = ReaderTheme.SEPIA))
                awaitDecoration(host.navigator)
                host.engine.cfiNavigator.goTo(EpubCfi(CROSS_SPINE_POINT_CFI)).requireSuccess()
                awaitDecoration(host.navigator)
            }

            scenario.recreate()
            val recreated = scenario.awaitReadyHost()
            runBlocking { awaitDecoration(recreated.navigator) }
            assertSame(host.engine, recreated.engine)
        }
    }

    @Test
    fun decorationActivationPreservesCurrentAndPreviousIdentityAfterRecreation() = withFixture(
        "annotation-activation.epub"
    ) { fixture ->
        launchHost(fixture).use { scenario ->
            val first = scenario.awaitReadyHost()
            val current = annotationDecoration("current-highlight", EpubCfi(CROSS_MARKUP_RANGE_CFI))
            runBlocking {
                first.engine.annotationDecorations.replace(
                    ReaderAnnotationDecorationGroupId.Current,
                    listOf(current)
                )
                awaitDecoration(first.navigator)
            }
            val currentActivation = awaitActivation(first.engine) {
                activateDecoration(first.navigator, "second-pass-current-session-annotations")
            }
            assertEquals("session-1", currentActivation.sessionId)
            assertEquals(ReaderAnnotationDecorationGroupId.Current, currentActivation.groupId)
            assertEquals("current-highlight", currentActivation.annotationId)

            val previousGroup = ReaderAnnotationDecorationGroupId.Previous("previous-session")
            val previous = annotationDecoration(
                "previous-highlight",
                EpubCfi(CROSS_MARKUP_RANGE_CFI),
                sessionId = "previous-session"
            )
            runBlocking {
                first.engine.annotationDecorations.clear(ReaderAnnotationDecorationGroupId.Current)
                first.engine.annotationDecorations.replace(previousGroup, listOf(previous))
                awaitDecorationGroup(
                    first.navigator,
                    "second-pass-previous-session-previous-session"
                )
            }
            val previousActivation = awaitActivation(first.engine) {
                activateDecoration(
                    first.navigator,
                    "second-pass-previous-session-previous-session"
                )
            }
            assertEquals("previous-session", previousActivation.sessionId)
            assertEquals(previousGroup, previousActivation.groupId)
            assertEquals("previous-highlight", previousActivation.annotationId)

            scenario.recreate()
            val recreated = scenario.awaitReadyHost()
            runBlocking {
                awaitDecorationGroup(
                    recreated.navigator,
                    "second-pass-previous-session-previous-session"
                )
            }
            val rebound = awaitActivation(recreated.engine) {
                activateDecoration(
                    recreated.navigator,
                    "second-pass-previous-session-previous-session"
                )
            }
            assertEquals(previousActivation, rebound)
            assertSame(first.engine, recreated.engine)
        }
    }

    @Test
    fun liveDocumentSelectionObserverTracksSelectionClearAndNavigatorRecreation() = withFixture(
        "live-selection-observer.epub"
    ) { fixture ->
        launchHost(fixture).use { scenario ->
            val first = scenario.awaitReadyHost()
            val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val selectionController = ReaderSelectionController(eventScope).also {
                it.attach(first.engine.selectionEvents, first.engine.cfiNavigator)
            }
            runBlocking { awaitSelectionObserver(first.navigator) }

            val selected = eventScope.async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(HOST_TIMEOUT_MILLIS) {
                    selectionController.selection.first { it != null }
                }
            }
            runBlocking {
                withContext(Dispatchers.Main) {
                    first.navigator.evaluateJavascript(CROSS_MARKUP_SELECTION_SCRIPT)
                }
                selected.await()
            }
            val captured = selectionController.selection.value
            assertNotNull(captured)
            assertNotNull(requireNotNull(captured).bounds)
            assertTrue(requireNotNull(captured.bounds).right >= captured.bounds.left)

            val cleared = eventScope.async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(HOST_TIMEOUT_MILLIS) {
                    selectionController.selection.first { it == null }
                }
            }
            runBlocking {
                withContext(Dispatchers.Main) {
                    first.navigator.evaluateJavascript(
                        "window.getSelection().removeAllRanges(); true;"
                    )
                }
                cleared.await()
            }
            assertNull(selectionController.selection.value)

            scenario.recreate()
            val recreated = scenario.awaitReadyHost()
            runBlocking { awaitSelectionObserver(recreated.navigator) }
            val rebound = eventScope.async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(HOST_TIMEOUT_MILLIS) {
                    selectionController.selection.first { it != null }
                }
            }
            runBlocking {
                withContext(Dispatchers.Main) {
                    recreated.navigator.evaluateJavascript(CROSS_MARKUP_SELECTION_SCRIPT)
                }
                rebound.await()
            }
            assertNotNull(selectionController.selection.value)
            selectionController.detach()
            eventScope.cancel()
        }
    }

    @Test
    fun previousSessionDecorationGroupsHideIndependentlyAndReapply() = withFixture(
        "annotation-decorations.epub"
    ) { fixture ->
        val first = ReaderAnnotationDecorationGroupId.Previous("session-a")
        val second = ReaderAnnotationDecorationGroupId.Previous("session-b")
        launchHost(fixture).use { scenario ->
            val host = scenario.awaitReadyHost()
            runBlocking {
                val sameIdentity = annotationDecoration(
                    "same-client",
                    EpubCfi(CROSS_MARKUP_RANGE_CFI)
                )
                host.engine.annotationDecorations.replace(first, listOf(sameIdentity))
                host.engine.annotationDecorations.replace(second, listOf(sameIdentity))
                awaitDecorationGroup(host.navigator, "second-pass-previous-session-session-a")
                awaitDecorationGroup(host.navigator, "second-pass-previous-session-session-b")

                host.engine.annotationDecorations.clear(first)
                awaitDecorationGroup(
                    host.navigator,
                    "second-pass-previous-session-session-a",
                    expected = false
                )
                awaitDecorationGroup(host.navigator, "second-pass-previous-session-session-b")
            }

            scenario.recreate()
            val recreated = scenario.awaitReadyHost()
            runBlocking {
                awaitDecorationGroup(recreated.navigator, "second-pass-previous-session-session-b")
            }
            assertSame(host.engine, recreated.engine)
        }
    }

    @Test
    fun visibleBookmarksFilterOtherResourcesAndChunkWithoutDroppingCandidates() = withFixture(
        "bookmark-prefilter-and-chunking.epub"
    ) { fixture ->
        launchHost(fixture).use { scenario ->
            val engine = scenario.awaitReadyHost().engine
            val visibleCfi = runBlocking {
                engine.cfiNavigator.currentPosition().requireSuccess().value
            }
            val visible = (0..1_000).map { index ->
                ReaderAnnotation.Bookmark(
                    id = "visible-$index",
                    clientId = "visible-$index",
                    cfi = visibleCfi,
                    locationLabel = "Visible $index",
                    updatedAt = "2026-08-31T00:00:00Z"
                )
            }
            val otherResource = ReaderAnnotation.Bookmark(
                id = "other-resource",
                clientId = "other-resource",
                cfi = CROSS_SPINE_POINT_CFI,
                locationLabel = "Chapter Two",
                updatedAt = "2026-08-31T00:00:00Z"
            )

            val result = runBlocking {
                engine.visiblePageBookmarks.resolve(visible + otherResource)
            }

            assertEquals(
                visible.map(ReaderAnnotation.Bookmark::id),
                result.bookmarks.map { it.id }
            )
        }
    }
}
