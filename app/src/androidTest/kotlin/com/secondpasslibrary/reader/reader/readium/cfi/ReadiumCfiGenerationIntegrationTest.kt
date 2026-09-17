package com.secondpasslibrary.reader.reader.readium.cfi

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiFailure
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import com.secondpasslibrary.reader.reader.cfi.EpubCfiTargetKind
import com.secondpasslibrary.reader.reader.cfi.SyntheticEpubCfiSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ReadiumCfiGenerationIntegrationTest : ReadiumEpubCfiNavigatorTestSupport() {
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
    fun protocolFailuresRemainTypedAndMissingDomTargetRetries() = withFixture(
        "protocol-compatibility.epub"
    ) { fixture ->
        launchHost(fixture).use { scenario ->
            val host = scenario.awaitReadyHost()
            val target = EpubCfi(CROSS_MARKUP_RANGE_CFI)
            runBlocking { host.engine.cfiNavigator.resolve(target).requireSuccess() }
            fun replaceContent(body: String) = runBlocking {
                withContext(Dispatchers.Main) {
                    host.navigator.evaluateJavascript(
                        """
                        (() => {
                          const original = window.__cfiOriginal || window.__secondPassEpubCfi;
                          window.__cfiOriginal = original;
                          window.__secondPassEpubCfi = { ...original, resolveContent: function(...args) {
                            $body
                          }};
                        })();
                        """.trimIndent()
                    )
                }
            }

            replaceContent("return { ok: false, error: { code: 'UNKNOWN_ERROR' } };")
            assertEquals(
                EpubCfiOutcome.Failure(EpubCfiFailure.CFI_RUNTIME_FAILURE),
                runBlocking { host.engine.cfiNavigator.resolve(target) }
            )
            replaceContent("return { ok: true, value: { kind: 'unknown', movementAnchor: {} } };")
            assertEquals(
                EpubCfiOutcome.Failure(EpubCfiFailure.CFI_RUNTIME_FAILURE),
                runBlocking { host.engine.cfiNavigator.resolve(target) }
            )
            replaceContent("return undefined;")
            assertEquals(
                EpubCfiOutcome.Failure(EpubCfiFailure.JAVASCRIPT_RUNTIME_UNAVAILABLE),
                runBlocking { host.engine.cfiNavigator.resolve(target) }
            )
            replaceContent(
                """
                window.__cfiResolveAttempts = (window.__cfiResolveAttempts || 0) + 1;
                if (window.__cfiResolveAttempts === 1) {
                  return { ok: false, error: { code: 'DOM_TARGET_NOT_FOUND' } };
                }
                return original.resolveContent(...args);
                """.trimIndent()
            )
            runBlocking { host.engine.cfiNavigator.goTo(target).requireSuccess() }
            val attempts = runBlocking {
                withContext(Dispatchers.Main) {
                    host.navigator.evaluateJavascript("window.__cfiResolveAttempts")
                }
            }
            assertTrue(requireNotNull(attempts).toInt() >= 2)
        }
    }
}
