package com.secondpasslibrary.reader.reader.readium.cfi

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecoration
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecorationActivation
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecorationGroupId
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationKind
import com.secondpasslibrary.reader.reader.annotations.selection.ReaderSelectionController
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearance
import com.secondpasslibrary.reader.reader.appearance.ReaderTheme
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiFailure
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import com.secondpasslibrary.reader.reader.cfi.EpubCfiReadiness
import com.secondpasslibrary.reader.reader.cfi.EpubCfiSelection
import com.secondpasslibrary.reader.reader.cfi.EpubCfiTargetKind
import com.secondpasslibrary.reader.reader.cfi.SyntheticEpubCfiSources
import com.secondpasslibrary.reader.reader.cfi.SyntheticEpubFixtureBuilder
import com.secondpasslibrary.reader.reader.cfi.normalizeEpubHref
import com.secondpasslibrary.reader.reader.domain.ReaderEngine
import com.secondpasslibrary.reader.reader.toc.ReaderPublicationNavigationResult
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

private const val HOST_TIMEOUT_MILLIS = 30_000L

private fun annotationDecoration(
    id: String,
    cfi: EpubCfi,
    sessionId: String = "session-1"
): ReaderAnnotationDecoration = ReaderAnnotationDecoration(
    sessionId = sessionId,
    annotationId = id,
    cfi = cfi,
    kind = ReaderAnnotationKind.HIGHLIGHT,
    color = ReaderAnnotationColor.YELLOW
)

@RunWith(AndroidJUnit4::class)
class ReadiumEpubCfiNavigatorIntegrationTest {
    private val targetContext: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun cfiReadinessWaitsForViewportAttachment() = withFixture(
        "deferred-viewport.epub"
    ) { fixture ->
        launchHost(fixture, deferViewport = true).use { scenario ->
            val engine = scenario.awaitOpenedEngine()
            assertEquals(EpubCfiReadiness.AwaitingViewport, engine.cfiNavigator.readiness.value)
            val beforeBind = runBlocking { engine.cfiNavigator.currentPosition() }
            assertEquals(
                EpubCfiOutcome.Failure(EpubCfiFailure.NAVIGATOR_UNAVAILABLE),
                beforeBind
            )

            val waiterScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val readiness = waiterScope.async {
                engine.cfiNavigator.awaitNavigationAvailable()
            }
            scenario.onActivity(ReadiumCfiTestActivity::attachViewport)

            assertEquals(
                EpubCfiOutcome.Success(Unit),
                runBlocking { withTimeout(HOST_TIMEOUT_MILLIS) { readiness.await() } }
            )
            assertEquals(EpubCfiReadiness.Available, engine.cfiNavigator.readiness.value)
            runBlocking { engine.cfiNavigator.currentPosition().requireSuccess() }
            waiterScope.cancel()
        }
    }

    @Test
    fun pendingBoundOperationEndsWhenNavigatorIsRecreated() = withFixture(
        "pending-recreation.epub"
    ) { fixture ->
        launchHost(fixture).use { scenario ->
            val host = scenario.awaitReadyHost()
            val testBinding = ReadiumCfiNavigatorBinding(
                ReadiumCfiJavascriptRuntime(targetContext)
            )
            scenario.onActivity { testBinding.bind(host.navigator) }
            assertEquals(
                EpubCfiReadiness.Available,
                runBlocking {
                    withTimeout(HOST_TIMEOUT_MILLIS) {
                        testBinding.readiness.first { it == EpubCfiReadiness.Available }
                    }
                }
            )
            val operationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val started = kotlinx.coroutines.CompletableDeferred<Unit>()
            val pending = operationScope.async {
                testBinding.withNavigator { _, _ ->
                    started.complete(Unit)
                    awaitCancellation()
                }
            }
            runBlocking { withTimeout(HOST_TIMEOUT_MILLIS) { started.await() } }

            scenario.onActivity { testBinding.unbind(host.navigator) }
            assertNull(runBlocking { withTimeout(HOST_TIMEOUT_MILLIS) { pending.await() } })
            assertEquals(EpubCfiReadiness.AwaitingViewport, testBinding.readiness.value)

            scenario.recreate()
            val recreated = scenario.awaitReadyHost()
            scenario.onActivity { testBinding.bind(recreated.navigator) }
            assertEquals(
                EpubCfiReadiness.Available,
                runBlocking {
                    withTimeout(HOST_TIMEOUT_MILLIS) {
                        testBinding.readiness.first { it == EpubCfiReadiness.Available }
                    }
                }
            )
            testBinding.close()
            scenario.onActivity { testBinding.unbind(recreated.navigator) }
            assertEquals(EpubCfiReadiness.Closed, testBinding.readiness.value)
            operationScope.cancel()
        }
    }

    @Test
    fun realNavigatorRoundTripsPointAndCrossMarkupRange() = withFixture(
        "point-range.epub"
    ) { fixture ->
        launchHost(fixture).use { scenario ->
            val host = scenario.awaitReadyHost()
            val point = runBlocking {
                host.engine.cfiNavigator.currentPosition().requireSuccess()
            }
            val pointResolution = runBlocking {
                host.engine.cfiNavigator.resolve(point).requireSuccess()
            }

            assertEquals(EpubCfiTargetKind.POINT, pointResolution.kind)
            assertEquals(SyntheticEpubCfiSources.CHAPTER_ONE_PATH, pointResolution.resourceHref)

            val knownRange = EpubCfi(CROSS_MARKUP_RANGE_CFI)
            val knownRangeResolution = runBlocking {
                host.engine.cfiNavigator.resolve(knownRange).requireSuccess()
            }
            assertEquals(EpubCfiTargetKind.RANGE, knownRangeResolution.kind)
            assertEquals(CROSS_MARKUP_EXPECTED_TEXT, knownRangeResolution.selectedText)

            runBlocking {
                host.engine.cfiNavigator.goTo(EpubCfi(CROSS_SPINE_POINT_CFI)).requireSuccess()
                host.engine.cfiNavigator.goTo(point).requireSuccess()
                host.engine.cfiNavigator.goTo(knownRange).requireSuccess()
            }
            assertCurrentResource(host.engine, SyntheticEpubCfiSources.CHAPTER_ONE_PATH)

            runBlocking {
                withContext(Dispatchers.Main) {
                    host.navigator.evaluateJavascript(CROSS_MARKUP_SELECTION_SCRIPT)
                }
            }
            val selection = runBlocking {
                host.engine.cfiNavigator.currentSelection().requireSuccess()
            }

            assertNotNull(selection)
            assertTrue(requireNotNull(selection).selectedText.contains("nested"))
            assertEquals(1, selection.chapterOrdinal)
            assertNotNull(selection.totalProgression)
            assertTrue(requireNotNull(selection.totalProgression) in 0.0..1.0)
            assertRangeRoundTrip(host.engine, requireNotNull(selection))
        }
    }

    @Test
    fun crossSpineNavigationAndVisiblePositionSurviveNavigatorRecreation() = withFixture(
        "cross-spine-recreation.epub"
    ) { fixture ->
        launchHost(fixture).use { scenario ->
            val initialHost = scenario.awaitReadyHost()
            val originalEngine = initialHost.engine
            val target = EpubCfi(CROSS_SPINE_POINT_CFI)
            val originalPosition = runBlocking {
                originalEngine.cfiNavigator.currentPosition().requireSuccess()
            }

            runBlocking { originalEngine.cfiNavigator.goTo(target).requireSuccess() }
            assertCurrentResource(originalEngine, SyntheticEpubCfiSources.CHAPTER_TWO_PATH)

            scenario.recreate()
            val recreatedHost = scenario.awaitReadyHost()

            assertSame(originalEngine, recreatedHost.engine)
            runBlocking {
                recreatedHost.engine.cfiNavigator.currentPosition().requireSuccess()
                recreatedHost.engine.cfiNavigator.goTo(target).requireSuccess()
                recreatedHost.engine.cfiNavigator.goTo(originalPosition).requireSuccess()
            }
            assertCurrentResource(
                recreatedHost.engine,
                SyntheticEpubCfiSources.CHAPTER_ONE_PATH
            )
        }
    }

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
    fun activityRecreationRestoresExactTransientReadingPosition() = withFixture(
        "position-retention.epub"
    ) { fixture ->
        launchHost(fixture).use { scenario ->
            val initial = scenario.awaitReadyHost()
            initial.engine.positionRetention.completeStartupRestore(null)
            runBlocking {
                initial.engine.cfiNavigator.goTo(EpubCfi(CROSS_SPINE_POINT_CFI))
                    .requireSuccess()
            }
            val before = runBlocking {
                initial.engine.cfiNavigator.currentPosition().requireSuccess()
            }
            assertTrue(runBlocking { scenario.isPassageVisible("cross-spine-target") })
            initial.engine.positionRetention.retainPosition(before)

            scenario.recreate()
            val recreated = scenario.awaitReadyHost()
            assertSame(initial.engine, recreated.engine)
            runBlocking {
                repeat(100) {
                    if (scenario.isPassageVisible("cross-spine-target")) return@runBlocking
                    delay(50)
                }
            }
            assertTrue(
                "The cross-spine passage was not restored after Activity recreation.",
                runBlocking { scenario.isPassageVisible("cross-spine-target") }
            )
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
    fun rapidNavigationKeepsNewestCfiAsFinalDestination() = withFixture(
        "latest-navigation.epub"
    ) { fixture ->
        launchHost(fixture).use { scenario ->
            val host = scenario.awaitReadyHost()
            val originalPosition = runBlocking {
                host.engine.cfiNavigator.currentPosition().requireSuccess()
            }

            runBlocking {
                val first = async {
                    host.engine.cfiNavigator.goTo(EpubCfi(CROSS_SPINE_POINT_CFI))
                }
                val second = async { host.engine.cfiNavigator.goTo(originalPosition) }
                runCatching { first.await() }
                second.await().requireSuccess()
            }

            assertCurrentResource(host.engine, SyntheticEpubCfiSources.CHAPTER_ONE_PATH)
        }
    }

    @Test
    fun positionCaptureDuringCrossResourceArrivalDoesNotCancelNavigation() = withFixture(
        "navigation-priority.epub"
    ) { fixture ->
        launchHost(fixture).use { scenario ->
            val host = scenario.awaitReadyHost()

            runBlocking {
                val navigation = async(start = CoroutineStart.UNDISPATCHED) {
                    host.engine.cfiNavigator.goTo(EpubCfi(CROSS_SPINE_POINT_CFI))
                }
                withTimeout(HOST_TIMEOUT_MILLIS) {
                    host.navigator.currentLocator.first { locator ->
                        normalizeEpubHref(locator.href.toString()) ==
                            SyntheticEpubCfiSources.CHAPTER_TWO_PATH
                    }
                }
                assertFalse(navigation.isCompleted)
                val positionCapture = async {
                    host.engine.cfiNavigator.currentPositionWithContext()
                }

                navigation.await().requireSuccess()
                positionCapture.await().requireSuccess()
            }

            assertCurrentResource(host.engine, SyntheticEpubCfiSources.CHAPTER_TWO_PATH)
        }
    }

    @Test
    fun positionCaptureWaitsForInFlightTocNavigation() = withFixture(
        "toc-read-priority.epub"
    ) { fixture ->
        launchHost(fixture).use { scenario ->
            val host = scenario.awaitReadyHost()
            val chapterTwo = requireNotNull(
                host.engine.tableOfContents.entries.single { it.title == "Chapter Two" }.target
            )

            runBlocking {
                val tocNavigation = async(start = CoroutineStart.UNDISPATCHED) {
                    host.engine.tableOfContents.goTo(chapterTwo)
                }
                assertFalse(tocNavigation.isCompleted)
                val positionCapture = async {
                    host.engine.cfiNavigator.currentPositionWithContext()
                }

                assertEquals(
                    ReaderPublicationNavigationResult.NAVIGATED,
                    tocNavigation.await()
                )
                positionCapture.await().requireSuccess()
            }

            awaitPublicationResource(host.engine, SyntheticEpubCfiSources.CHAPTER_TWO_PATH)
            assertCurrentResource(host.engine, SyntheticEpubCfiSources.CHAPTER_TWO_PATH)
        }
    }

    @Test
    fun cfiNavigationSupersedesInFlightTocNavigation() = withFixture(
        "cfi-supersedes-toc.epub"
    ) { fixture ->
        launchHost(fixture).use { scenario ->
            val host = scenario.awaitReadyHost()
            val originalPosition = runBlocking {
                host.engine.cfiNavigator.currentPosition().requireSuccess()
            }
            val chapterTwo = requireNotNull(
                host.engine.tableOfContents.entries.single { it.title == "Chapter Two" }.target
            )

            runBlocking {
                val tocNavigation = async(start = CoroutineStart.UNDISPATCHED) {
                    host.engine.tableOfContents.goTo(chapterTwo)
                }
                assertFalse(tocNavigation.isCompleted)
                val cfiNavigation = async {
                    host.engine.cfiNavigator.goTo(originalPosition)
                }

                runCatching { tocNavigation.await() }
                cfiNavigation.await().requireSuccess()
            }

            assertCurrentResource(host.engine, SyntheticEpubCfiSources.CHAPTER_ONE_PATH)
        }
    }

    @Test
    fun tocNavigationSupersedesInFlightCfiNavigation() = withFixture(
        "toc-supersedes-cfi.epub"
    ) { fixture ->
        launchHost(fixture).use { scenario ->
            val host = scenario.awaitReadyHost()
            val chapterOne = requireNotNull(
                host.engine.tableOfContents.entries.single { it.title == "Chapter One" }.target
            )

            runBlocking {
                val cfiNavigation = async(start = CoroutineStart.UNDISPATCHED) {
                    host.engine.cfiNavigator.goTo(EpubCfi(CROSS_SPINE_POINT_CFI))
                }
                withTimeout(HOST_TIMEOUT_MILLIS) {
                    host.navigator.currentLocator.first { locator ->
                        normalizeEpubHref(locator.href.toString()) ==
                            SyntheticEpubCfiSources.CHAPTER_TWO_PATH
                    }
                }
                assertFalse(cfiNavigation.isCompleted)
                val tocNavigation = async {
                    host.engine.tableOfContents.goTo(chapterOne)
                }

                runCatching { cfiNavigation.await() }
                assertEquals(
                    ReaderPublicationNavigationResult.NAVIGATED,
                    tocNavigation.await()
                )
            }

            awaitPublicationResource(host.engine, SyntheticEpubCfiSources.CHAPTER_ONE_PATH)
            assertCurrentResource(host.engine, SyntheticEpubCfiSources.CHAPTER_ONE_PATH)
        }
    }

    @Test
    fun resourceTransitionDuringBoundCaptureIsRejected() = withFixture(
        "resource-coherence.epub"
    ) { fixture ->
        launchHost(fixture).use { scenario ->
            val host = scenario.awaitReadyHost()
            val testBinding = ReadiumCfiNavigatorBinding(
                ReadiumCfiJavascriptRuntime(targetContext)
            )
            scenario.onActivity { testBinding.bind(host.navigator) }
            runBlocking {
                withTimeout(HOST_TIMEOUT_MILLIS) {
                    testBinding.readiness.first { it == EpubCfiReadiness.Available }
                }
            }

            val capture = runBlocking {
                testBinding.withNavigator { navigator, _ ->
                    val before = testBinding.resourceIdentity(navigator)
                    val moved = navigator.go(
                        Link(
                            href = requireNotNull(
                                Url(SyntheticEpubCfiSources.CHAPTER_TWO_PATH)
                            ),
                            mediaType = requireNotNull(MediaType("application/xhtml+xml"))
                        ),
                        animated = false
                    )
                    assertTrue(moved)
                    withTimeout(HOST_TIMEOUT_MILLIS) {
                        navigator.currentLocator.first { locator ->
                            normalizeEpubHref(locator.href.toString()) ==
                                SyntheticEpubCfiSources.CHAPTER_TWO_PATH
                        }
                    }
                    val coherent = coherentResourceCapture(
                        before,
                        testBinding.resourceIdentity(navigator),
                        "captured content"
                    )
                    assertEquals(
                        EpubCfiReadiness.PreparingDocument,
                        testBinding.readiness.value
                    )
                    coherent
                }
            }

            assertEquals(ReadiumCfiResourceCapture.Changed, capture)
            assertEquals(
                EpubCfiReadiness.Available,
                runBlocking {
                    withTimeout(HOST_TIMEOUT_MILLIS) {
                        testBinding.readiness.first { it == EpubCfiReadiness.Available }
                    }
                }
            )
            testBinding.close()
        }
    }

    private fun assertRangeRoundTrip(engine: ReaderEngine, selection: EpubCfiSelection) {
        runBlocking {
            engine.cfiNavigator.goTo(EpubCfi(CROSS_SPINE_POINT_CFI)).requireSuccess()
            engine.cfiNavigator.goTo(selection.cfi).requireSuccess()
        }
        val resolution = runBlocking {
            engine.cfiNavigator.resolve(selection.cfi).requireSuccess()
        }
        assertEquals(EpubCfiTargetKind.RANGE, resolution.kind)
        assertEquals(selection.selectedText, resolution.selectedText)
        assertEquals(selection.prefix, resolution.prefix)
        assertEquals(selection.suffix, resolution.suffix)
    }

    private fun assertCurrentResource(engine: ReaderEngine, expectedHref: String) {
        val current = runBlocking {
            engine.cfiNavigator.currentPosition().requireSuccess()
        }
        val resolution = runBlocking {
            engine.cfiNavigator.resolve(current).requireSuccess()
        }
        assertEquals(expectedHref, resolution.resourceHref)
    }

    private fun awaitPublicationResource(engine: ReaderEngine, expectedHref: String) {
        runBlocking {
            withTimeout(HOST_TIMEOUT_MILLIS) {
                engine.tableOfContents.currentResource.first { it?.reference == expectedHref }
            }
        }
    }

    private fun launchHost(
        fixture: File,
        deferViewport: Boolean = false
    ): ActivityScenario<ReadiumCfiTestActivity> {
        val intent = Intent(targetContext, ReadiumCfiTestActivity::class.java)
            .putExtra(ReadiumCfiTestActivity.EXTRA_EPUB_PATH, fixture.absolutePath)
            .putExtra(ReadiumCfiTestActivity.EXTRA_DEFER_VIEWPORT, deferViewport)
        return ActivityScenario.launch(intent)
    }

    private suspend fun awaitDecoration(navigator: EpubNavigatorFragment) {
        withTimeout(HOST_TIMEOUT_MILLIS) {
            var installed = false
            while (!installed) {
                installed = withContext(Dispatchers.Main) {
                    navigator.evaluateJavascript(
                        "Boolean(document.querySelector(" +
                            "'div[data-group=\\\"second-pass-current-session-annotations\\\"]'))"
                    ) == "true"
                }
                if (!installed) delay(50)
            }
        }
    }

    private suspend fun awaitSelectionObserver(navigator: EpubNavigatorFragment) {
        withTimeout(HOST_TIMEOUT_MILLIS) {
            var installed = false
            while (!installed) {
                installed = withContext(Dispatchers.Main) {
                    navigator.evaluateJavascript(
                        "Boolean(window.__secondPassSelectionObserver)"
                    ) == "true"
                }
                if (!installed) delay(50)
            }
        }
    }

    private suspend fun awaitDecorationGroup(
        navigator: EpubNavigatorFragment,
        group: String,
        expected: Boolean = true
    ) {
        withTimeout(HOST_TIMEOUT_MILLIS) {
            var matches = false
            while (!matches) {
                matches = withContext(Dispatchers.Main) {
                    val present = navigator.evaluateJavascript(
                        "Boolean(document.querySelector('div[data-group=\"$group\"]'))"
                    ) == "true"
                    present == expected
                }
                if (!matches) delay(50)
            }
        }
    }

    private fun awaitActivation(
        engine: ReaderEngine,
        activate: suspend () -> Unit
    ): ReaderAnnotationDecorationActivation = runBlocking {
        val activation = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(HOST_TIMEOUT_MILLIS) {
                engine.annotationDecorations.activations.first()
            }
        }
        activate()
        activation.await()
    }

    private suspend fun activateDecoration(navigator: EpubNavigatorFragment, group: String) {
        withContext(Dispatchers.Main) {
            val activated = navigator.evaluateJavascript(
                """
                (() => {
                  const container = document.querySelector('div[data-group="$group"]');
                  const target = container && container.firstElementChild &&
                    container.firstElementChild.firstElementChild;
                  if (!target) return false;
                  const rect = target.getBoundingClientRect();
                  target.dispatchEvent(new MouseEvent('click', {
                    bubbles: true,
                    clientX: rect.left + rect.width / 2,
                    clientY: rect.top + rect.height / 2
                  }));
                  return true;
                })();
                """.trimIndent()
            )
            check(activated == "true") { "Readium decoration target was not activatable." }
        }
    }

    private fun ActivityScenario<ReadiumCfiTestActivity>.awaitOpenedEngine(): ReaderEngine {
        lateinit var state: StateFlow<ReadiumCfiTestHostState>
        onActivity { activity -> state = activity.hostState }
        val hostState = runBlocking {
            withTimeout(HOST_TIMEOUT_MILLIS) {
                state.first { it !is ReadiumCfiTestHostState.Loading }
            }
        }
        check(hostState is ReadiumCfiTestHostState.Ready) {
            "Reader test host failed: ${(hostState as ReadiumCfiTestHostState.Failed).reason}"
        }
        return hostState.engine
    }

    private fun ActivityScenario<ReadiumCfiTestActivity>.awaitReadyHost(): ReadyHost {
        lateinit var generation: StateFlow<Int>
        onActivity { activity ->
            generation = activity.navigatorGeneration
        }
        val engine = awaitOpenedEngine()
        runBlocking {
            withTimeout(HOST_TIMEOUT_MILLIS) {
                generation.first { it > 0 }
            }
            engine.cfiNavigator.awaitNavigationAvailable().requireSuccess()
        }
        lateinit var navigator: EpubNavigatorFragment
        onActivity { activity ->
            navigator = requireNotNull(activity.currentNavigator())
        }
        return ReadyHost(engine, navigator)
    }

    private suspend fun ActivityScenario<ReadiumCfiTestActivity>.isPassageVisible(
        id: String
    ): Boolean {
        lateinit var navigator: EpubNavigatorFragment
        onActivity { activity ->
            navigator = requireNotNull(activity.currentNavigator())
        }
        return withContext(Dispatchers.Main) {
            navigator.evaluateJavascript(
                """
                (() => {
                  const element = document.getElementById(${JSONObject.quote(id)});
                  if (!element) return false;
                  const rect = element.getBoundingClientRect();
                  return rect.right > 0 && rect.bottom > 0 &&
                    rect.left < window.innerWidth && rect.top < window.innerHeight;
                })();
                """.trimIndent()
            ) == "true"
        }
    }

    private fun withFixture(fileName: String, block: (File) -> Unit) {
        val fixture = SyntheticEpubFixtureBuilder.create(
            targetContext.cacheDir.resolve("reader-cfi-integration"),
            fileName
        )
        try {
            block(fixture)
        } finally {
            fixture.delete()
        }
    }
}

private data class ReadyHost(val engine: ReaderEngine, val navigator: EpubNavigatorFragment)

private fun <T> EpubCfiOutcome<T>.requireSuccess(): T = when (this) {
    is EpubCfiOutcome.Success -> value
    is EpubCfiOutcome.Failure -> error("Expected CFI success, got $reason")
}

private const val CROSS_SPINE_POINT_CFI =
    "epubcfi(/6/4[spine-chapter-two]!/4/2[chapter-two-root]/4[cross-spine-target]/1:4)"

private const val CROSS_SPINE_RANGE_CFI =
    "epubcfi(/6/4[spine-chapter-two]!/4/2[chapter-two-root]/4[cross-spine-target],/1:4,/1:10)"

private const val CROSS_MARKUP_RANGE_CFI =
    "epubcfi(/6/2[spine-chapter-one]!/4/2[chapter-one-root]/6[inline-markup]," +
        "/1:2,/2[nested-span]/2[nested-emphasis]/1:4)"

private const val CROSS_MARKUP_EXPECTED_TEXT = "fore nested inli"

private val CROSS_MARKUP_SELECTION_SCRIPT =
    """
    (() => {
      const container = document.getElementById("inline-markup");
      const emphasis = document.getElementById("nested-emphasis");
      const range = document.createRange();
      range.setStart(container.firstChild, 2);
      range.setEnd(emphasis.firstChild, 4);
      const selection = window.getSelection();
      selection.removeAllRanges();
      selection.addRange(range);
      return selection.toString();
    })();
    """.trimIndent()
