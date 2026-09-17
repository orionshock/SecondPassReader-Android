package com.secondpasslibrary.reader.reader.readium.cfi

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecorationGroupId
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearance
import com.secondpasslibrary.reader.reader.appearance.ReaderLayoutMode
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiFailure
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import com.secondpasslibrary.reader.reader.cfi.EpubCfiReadiness
import com.secondpasslibrary.reader.reader.cfi.SyntheticEpubCfiSources
import com.secondpasslibrary.reader.reader.lifecycle.ReaderPositionRetentionController
import com.secondpasslibrary.reader.reader.toc.ReaderPublicationNavigationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ReadiumCfiNavigatorLifecycleIntegrationTest : ReadiumEpubCfiNavigatorTestSupport() {
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
    fun activityRecreationRefreshesExistingRetainedPositionBeforeNavigatorLoss() = withFixture(
        "fresh-position-retention.epub"
    ) { fixture ->
        launchHost(fixture).use { scenario ->
            val initial = scenario.awaitReadyHost()
            initial.engine.positionRetention.completeStartupRestore(null)
            initial.engine.positionRetention.retainPosition(EpubCfi(CROSS_MARKUP_RANGE_CFI))
            runBlocking {
                initial.engine.cfiNavigator.goTo(EpubCfi(CROSS_SPINE_POINT_CFI))
                    .requireSuccess()
            }
            assertTrue(runBlocking { scenario.isPassageVisible("cross-spine-target") })
            val retention = initial.engine.positionRetention as ReaderPositionRetentionController
            val retainedB = requireNotNull(
                runBlocking { retention.observePendingCapture() }
            )
            assertTrue(retainedB.value.contains("/6/4[spine-chapter-two]"))

            scenario.recreate()
            val recreated = scenario.awaitReadyHost()
            assertSame(initial.engine, recreated.engine)
            awaitPublicationResource(
                recreated.engine,
                SyntheticEpubCfiSources.CHAPTER_TWO_PATH
            )
            runBlocking {
                withTimeout(HOST_TIMEOUT_MILLIS) {
                    while (!scenario.isPassageVisible("cross-spine-target")) {
                        delay(50)
                    }
                }
            }
            assertTrue(
                "Retained $retainedB restored resource " +
                    recreated.engine.tableOfContents.currentResource.value,
                runBlocking {
                    scenario.isPassageVisible("cross-spine-target")
                }
            )
        }
    }

    @Test
    fun twoColumnRecreationRestoresTheRetainedLogicalSpread() = withFixture(
        "two-column-restore-fidelity.epub"
    ) { fixture ->
        launchHost(fixture).use { scenario ->
            var host = scenario.awaitReadyHost()
            val engine = host.engine
            runBlocking {
                engine.appearance.update(ReaderAppearance(layoutMode = ReaderLayoutMode.TWO_COLUMN))
                awaitRenderedColumnCount(host.navigator, 2)
                val chapterTwo = requireNotNull(
                    engine.tableOfContents.entries.single { it.title == "Chapter Two" }.target
                )
                assertEquals(
                    ReaderPublicationNavigationResult.NAVIGATED,
                    engine.tableOfContents.goTo(chapterTwo)
                )
            }

            RESTORE_VECTOR_INDICES.forEach { index ->
                val capture = runBlocking {
                    val target = "restore-vector-$index"
                    goForwardUntilTargetVisible(host.navigator, target)
                    assertEquals(target, selectRestoreMarker(host.navigator, index))
                    val targetSelection = requireNotNull(awaitCurrentSelection(engine))
                    assertEquals("restore-marker-$index", targetSelection.selectedText)
                    clearSelection(host.navigator)
                    val before = awaitSettledSpreadDiagnostics(
                        host.navigator,
                        target,
                        requireTargetVisible = true
                    )
                    val retained = engine.cfiNavigator.currentPosition().requireSuccess()
                    val resolved = engine.cfiNavigator.resolve(retained).requireSuccess()
                    assertEquals(SyntheticEpubCfiSources.CHAPTER_TWO_PATH, resolved.resourceHref)
                    RestoreCapture(
                        target = target,
                        retained = retained,
                        resolvedContext = "${resolved.prefix}|${resolved.suffix}",
                        before = before
                    )
                }

                scenario.recreate()
                host = scenario.awaitReadyHost()
                val after = runBlocking {
                    engine.cfiNavigator.awaitNavigationAvailable().requireSuccess()
                    engine.cfiNavigator.goTo(capture.retained).requireSuccess()
                    awaitSettledSpreadDiagnostics(host.navigator, capture.target)
                }

                assertEquals(
                    "Retained CFI ${capture.retained} resolved to ${capture.resolvedContext}; " +
                        "before=${capture.before} after=$after",
                    capture.before.currentSpreadIndex,
                    after.currentSpreadIndex
                )
                assertEquals(capture.before.visibleRestoreVectors, after.visibleRestoreVectors)
            }
        }
    }

    @Test
    fun explicitLayoutModesRepaginateAndRebindCoreReaderContracts() = withFixture(
        "explicit-layout-modes.epub"
    ) { fixture ->
        launchHost(fixture).use { scenario ->
            var host = scenario.awaitReadyHost()
            val engine = host.engine
            val chapterOne = requireNotNull(
                engine.tableOfContents.entries.single { it.title == "Chapter One" }.target
            )

            assertEquals(
                ReaderLayoutMode.SINGLE_COLUMN,
                engine.appearance.appearance.value.layoutMode
            )
            runBlocking { awaitRenderedColumnCount(host.navigator, 1) }
            assertLayoutNavigationContracts(engine, chapterOne)

            scenario.recreate()
            host = scenario.awaitReadyHost()
            assertSame(engine, host.engine)
            assertEquals(
                ReaderLayoutMode.SINGLE_COLUMN,
                engine.appearance.appearance.value.layoutMode
            )
            runBlocking { awaitRenderedColumnCount(host.navigator, 1) }

            runBlocking {
                val reloadedDocumentObserved = async(start = CoroutineStart.UNDISPATCHED) {
                    withTimeout(HOST_TIMEOUT_MILLIS) { engine.selectionEvents.changes().first() }
                }
                engine.appearance.update(ReaderAppearance(layoutMode = ReaderLayoutMode.TWO_COLUMN))
                awaitRenderedColumnCount(host.navigator, 2)
                reloadedDocumentObserved.await()
                engine.cfiNavigator.goTo(EpubCfi(CROSS_MARKUP_RANGE_CFI)).requireSuccess()
                val selectionChanged = async(start = CoroutineStart.UNDISPATCHED) {
                    withTimeout(HOST_TIMEOUT_MILLIS) { engine.selectionEvents.changes().first() }
                }
                withContext(Dispatchers.Main) {
                    host.navigator.evaluateJavascript(CROSS_MARKUP_SELECTION_SCRIPT)
                }
                selectionChanged.await()
                val selection = requireNotNull(
                    engine.cfiNavigator.currentSelection().requireSuccess()
                )
                assertRangeRoundTrip(engine, selection)
                engine.annotationDecorations.replace(
                    ReaderAnnotationDecorationGroupId.Current,
                    listOf(annotationDecoration("two-column", EpubCfi(CROSS_MARKUP_RANGE_CFI)))
                )
                awaitDecoration(host.navigator)
            }
            assertLayoutNavigationContracts(engine, chapterOne)

            scenario.recreate()
            host = scenario.awaitReadyHost()
            assertEquals(ReaderLayoutMode.TWO_COLUMN, engine.appearance.appearance.value.layoutMode)
            runBlocking {
                awaitRenderedColumnCount(host.navigator, 2)
                awaitDecoration(host.navigator)
                engine.cfiNavigator.goTo(EpubCfi(CROSS_SPINE_POINT_CFI)).requireSuccess()
                val bookmark = ReaderAnnotation.Bookmark(
                    id = "bookmark",
                    clientId = "layout-bookmark",
                    cfi = CROSS_SPINE_POINT_CFI,
                    locationLabel = "Chapter Two",
                    updatedAt = "2026-08-30T00:00:00Z"
                )
                assertEquals(
                    listOf(bookmark),
                    engine.visiblePageBookmarks.resolve(listOf(bookmark)).bookmarks
                )
                withTimeout(HOST_TIMEOUT_MILLIS) {
                    engine.hudEvents.readingStatus.first { it != null }
                }
            }

            runBlocking {
                engine.appearance.update(ReaderAppearance(layoutMode = ReaderLayoutMode.AUTO))
            }
            val autoColumns = runBlocking { awaitRenderedColumnCount(host.navigator) }
            assertTrue(autoColumns == 1 || autoColumns == 2)
            assertLayoutNavigationContracts(engine, chapterOne)

            scenario.recreate()
            host = scenario.awaitReadyHost()
            assertEquals(ReaderLayoutMode.AUTO, engine.appearance.appearance.value.layoutMode)
            assertEquals(autoColumns, runBlocking { awaitRenderedColumnCount(host.navigator) })
        }
    }
}
