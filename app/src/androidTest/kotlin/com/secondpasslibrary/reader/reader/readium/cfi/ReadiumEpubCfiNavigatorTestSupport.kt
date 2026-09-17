package com.secondpasslibrary.reader.reader.readium.cfi

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecoration
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecorationActivation
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecorationGroupId
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationKind
import com.secondpasslibrary.reader.reader.annotations.selection.ReaderSelectionController
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearance
import com.secondpasslibrary.reader.reader.appearance.ReaderLayoutMode
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
import com.secondpasslibrary.reader.reader.lifecycle.ReaderPositionRetentionController
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
import org.json.JSONTokener
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

internal const val HOST_TIMEOUT_MILLIS = 30_000L

internal fun annotationDecoration(
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

internal abstract class ReadiumEpubCfiNavigatorTestSupport {
    protected val targetContext: Context = ApplicationProvider.getApplicationContext()

    protected fun assertRangeRoundTrip(engine: ReaderEngine, selection: EpubCfiSelection) {
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

    protected fun assertCurrentResource(engine: ReaderEngine, expectedHref: String) {
        val current = runBlocking {
            engine.cfiNavigator.currentPosition().requireSuccess()
        }
        val resolution = runBlocking {
            engine.cfiNavigator.resolve(current).requireSuccess()
        }
        assertEquals(expectedHref, resolution.resourceHref)
    }

    protected fun assertLayoutNavigationContracts(
        engine: ReaderEngine,
        publicationTarget: com.secondpasslibrary.reader.reader.toc.ReaderPublicationTarget
    ) {
        runBlocking {
            engine.cfiNavigator.goTo(EpubCfi(CROSS_SPINE_POINT_CFI)).requireSuccess()
            assertEquals(
                EpubCfiTargetKind.POINT,
                engine.cfiNavigator.resolve(EpubCfi(CROSS_SPINE_POINT_CFI))
                    .requireSuccess().kind
            )
            assertEquals(
                ReaderPublicationNavigationResult.NAVIGATED,
                engine.tableOfContents.goTo(publicationTarget)
            )
            engine.cfiNavigator.currentPositionWithContext().requireSuccess()
        }
    }

    protected suspend fun awaitRenderedColumnCount(
        navigator: EpubNavigatorFragment,
        expected: Int? = null
    ): Int = withTimeout(HOST_TIMEOUT_MILLIS) {
        while (true) {
            val count = withContext(Dispatchers.Main) {
                navigator.evaluateJavascript(
                    "getComputedStyle(document.documentElement).columnCount"
                )?.trim('"')?.toIntOrNull()
            }
            if (count != null && (expected == null || count == expected)) return@withTimeout count
            delay(50)
        }
        error("Unreachable")
    }

    protected suspend fun selectRestoreMarker(
        navigator: EpubNavigatorFragment,
        index: Int
    ): String = withContext(Dispatchers.Main) {
        val id = "restore-vector-$index"
        val result = navigator.evaluateJavascript(
            """
            (() => {
              const element = document.getElementById(${JSONObject.quote(id)});
              const marker = ${JSONObject.quote("restore-marker-$index")};
              if (!element) return null;
              const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT);
              let node;
              while ((node = walker.nextNode())) {
                const offset = node.data.indexOf(marker);
                if (offset < 0) continue;
                const range = document.createRange();
                range.setStart(node, offset);
                range.setEnd(node, offset + marker.length);
                const selection = window.getSelection();
                selection.removeAllRanges();
                selection.addRange(range);
                return element.id;
              }
              return null;
            })();
            """.trimIndent()
        )
        check(result != "null") { "Restore marker $index was unavailable." }
        JSONTokener(result).nextValue() as String
    }

    protected suspend fun awaitCurrentSelection(engine: ReaderEngine): EpubCfiSelection? =
        withTimeout(HOST_TIMEOUT_MILLIS) {
            while (true) {
                when (val outcome = engine.cfiNavigator.currentSelection()) {
                    is EpubCfiOutcome.Success -> return@withTimeout outcome.value

                    EpubCfiOutcome.Failure(EpubCfiFailure.RESOURCE_CHANGED_DURING_OPERATION) ->
                        delay(50)

                    is EpubCfiOutcome.Failure -> outcome.requireSuccess()
                }
            }
            error("Unreachable")
        }

    protected suspend fun clearSelection(navigator: EpubNavigatorFragment) {
        withContext(Dispatchers.Main) {
            navigator.evaluateJavascript("window.getSelection()?.removeAllRanges(); true")
        }
    }

    protected suspend fun goForwardUntilTargetVisible(
        navigator: EpubNavigatorFragment,
        targetId: String
    ) = withTimeout(HOST_TIMEOUT_MILLIS) {
        while (!spreadDiagnostics(navigator, targetId).targetVisible) {
            val moved = withContext(Dispatchers.Main) { navigator.goForward(animated = false) }
            check(moved) { "Readium reached the end before $targetId became visible." }
            delay(50)
        }
    }

    protected suspend fun awaitSettledSpreadDiagnostics(
        navigator: EpubNavigatorFragment,
        targetId: String,
        requireTargetVisible: Boolean = false
    ): SpreadDiagnostics = withTimeout(HOST_TIMEOUT_MILLIS) {
        while (true) {
            val current = try {
                spreadDiagnostics(navigator, targetId)
            } catch (_: MissingRestoreTargetException) {
                delay(50)
                continue
            }
            if (!requireTargetVisible || current.targetVisible) {
                delay(250)
                return@withTimeout current
            }
            delay(50)
        }
        error("Unreachable")
    }

    protected suspend fun spreadDiagnostics(
        navigator: EpubNavigatorFragment,
        targetId: String
    ): SpreadDiagnostics = withContext(Dispatchers.Main) {
        val encoded = navigator.evaluateJavascript(
            """
            (() => {
              const target = document.getElementById(${JSONObject.quote(targetId)});
              if (!target) return JSON.stringify({ missing: true });
              const viewportWidth = document.documentElement.clientWidth || window.innerWidth;
              const viewportHeight = document.documentElement.clientHeight || window.innerHeight;
              const scrolling = document.scrollingElement;
              const scrollLeft = scrolling ? scrolling.scrollLeft : window.scrollX;
              const rect = target.getBoundingClientRect();
              const visible = rect.right > 0.5 && rect.left < viewportWidth - 0.5 &&
                rect.bottom > 0.5 && rect.top < viewportHeight - 0.5;
              const visibleIds = Array.from(document.querySelectorAll('[id^="restore-vector-"]'))
                .filter((element) => {
                  const candidate = element.getBoundingClientRect();
                  return candidate.right > 0.5 && candidate.left < viewportWidth - 0.5 &&
                    candidate.bottom > 0.5 && candidate.top < viewportHeight - 0.5;
                })
                .map((element) => element.id);
              return JSON.stringify({
                viewportWidth,
                viewportHeight,
                scrollLeft,
                scrollWidth: scrolling ? scrolling.scrollWidth : document.documentElement.scrollWidth,
                columnCount: parseInt(getComputedStyle(document.documentElement).columnCount) || 1,
                targetLeft: rect.left,
                targetRight: rect.right,
                targetVisible: visible,
                currentSpreadIndex: Math.round(scrollLeft / viewportWidth),
                targetSpreadIndex: Math.floor((scrollLeft + Math.max(rect.left, 0)) / viewportWidth),
                visibleIds
              });
            })();
            """.trimIndent()
        )
        val json = JSONObject(JSONTokener(encoded).nextValue() as String)
        if (json.optBoolean("missing")) throw MissingRestoreTargetException()
        SpreadDiagnostics(
            viewportWidth = json.getDouble("viewportWidth"),
            viewportHeight = json.getDouble("viewportHeight"),
            scrollLeft = json.getDouble("scrollLeft"),
            scrollWidth = json.getDouble("scrollWidth"),
            columnCount = json.getInt("columnCount"),
            targetLeft = json.getDouble("targetLeft"),
            targetRight = json.getDouble("targetRight"),
            targetVisible = json.getBoolean("targetVisible"),
            currentSpreadIndex = json.getInt("currentSpreadIndex"),
            targetSpreadIndex = json.getInt("targetSpreadIndex"),
            visibleRestoreVectors = buildList {
                val ids = json.getJSONArray("visibleIds")
                repeat(ids.length()) { add(ids.getString(it)) }
            }
        )
    }

    protected class MissingRestoreTargetException : IllegalStateException()

    protected fun awaitPublicationResource(engine: ReaderEngine, expectedHref: String) {
        runBlocking {
            withTimeout(HOST_TIMEOUT_MILLIS) {
                engine.tableOfContents.currentResource.first { it?.reference == expectedHref }
            }
        }
    }

    protected fun launchHost(
        fixture: File,
        deferViewport: Boolean = false
    ): ActivityScenario<ReadiumCfiTestActivity> {
        val intent = Intent(targetContext, ReadiumCfiTestActivity::class.java)
            .putExtra(ReadiumCfiTestActivity.EXTRA_EPUB_PATH, fixture.absolutePath)
            .putExtra(ReadiumCfiTestActivity.EXTRA_DEFER_VIEWPORT, deferViewport)
        return ActivityScenario.launch(intent)
    }

    protected suspend fun awaitDecoration(navigator: EpubNavigatorFragment) {
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

    protected suspend fun awaitSelectionObserver(navigator: EpubNavigatorFragment) {
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

    protected suspend fun awaitDecorationGroup(
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

    protected fun awaitActivation(
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

    protected suspend fun activateDecoration(navigator: EpubNavigatorFragment, group: String) {
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

    protected fun ActivityScenario<ReadiumCfiTestActivity>.awaitOpenedEngine(): ReaderEngine {
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

    protected fun ActivityScenario<ReadiumCfiTestActivity>.awaitReadyHost(): ReadyHost {
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

    protected suspend fun ActivityScenario<ReadiumCfiTestActivity>.isPassageVisible(
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

    protected fun withFixture(fileName: String, block: (File) -> Unit) {
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

internal data class ReadyHost(val engine: ReaderEngine, val navigator: EpubNavigatorFragment)

internal data class RestoreCapture(
    val target: String,
    val retained: EpubCfi,
    val resolvedContext: String,
    val before: SpreadDiagnostics
)

internal data class SpreadDiagnostics(
    val viewportWidth: Double,
    val viewportHeight: Double,
    val scrollLeft: Double,
    val scrollWidth: Double,
    val columnCount: Int,
    val targetLeft: Double,
    val targetRight: Double,
    val targetVisible: Boolean,
    val currentSpreadIndex: Int,
    val targetSpreadIndex: Int,
    val visibleRestoreVectors: List<String>
)

internal fun <T> EpubCfiOutcome<T>.requireSuccess(): T = when (this) {
    is EpubCfiOutcome.Success -> value
    is EpubCfiOutcome.Failure -> error("Expected CFI success, got $reason")
}

internal const val CROSS_SPINE_POINT_CFI =
    "epubcfi(/6/4[spine-chapter-two]!/4/2[chapter-two-root]/4[cross-spine-target]/1:4)"

internal const val CROSS_SPINE_RANGE_CFI =
    "epubcfi(/6/4[spine-chapter-two]!/4/2[chapter-two-root]/4[cross-spine-target],/1:4,/1:10)"

internal const val CROSS_MARKUP_RANGE_CFI =
    "epubcfi(/6/2[spine-chapter-one]!/4/2[chapter-one-root]/6[inline-markup]," +
        "/1:2,/2[nested-span]/2[nested-emphasis]/1:4)"

internal const val CROSS_MARKUP_EXPECTED_TEXT = "fore nested inli"
internal val RESTORE_VECTOR_INDICES = listOf(13, 37, 61)

internal val CROSS_MARKUP_SELECTION_SCRIPT =
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
