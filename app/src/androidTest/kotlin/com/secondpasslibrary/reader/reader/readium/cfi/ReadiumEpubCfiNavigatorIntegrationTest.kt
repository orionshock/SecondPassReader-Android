package com.secondpasslibrary.reader.reader.readium.cfi

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
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
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

private const val HOST_TIMEOUT_MILLIS = 30_000L

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

    private fun launchHost(
        fixture: File,
        deferViewport: Boolean = false
    ): ActivityScenario<ReadiumCfiTestActivity> {
        val intent = Intent(targetContext, ReadiumCfiTestActivity::class.java)
            .putExtra(ReadiumCfiTestActivity.EXTRA_EPUB_PATH, fixture.absolutePath)
            .putExtra(ReadiumCfiTestActivity.EXTRA_DEFER_VIEWPORT, deferViewport)
        return ActivityScenario.launch(intent)
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
