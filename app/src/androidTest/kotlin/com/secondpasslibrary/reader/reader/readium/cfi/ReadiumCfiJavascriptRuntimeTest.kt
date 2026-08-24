package com.secondpasslibrary.reader.reader.readium.cfi

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.reader.reader.cfi.SyntheticEpubCfiSources
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadiumCfiJavascriptRuntimeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun installsPinnedBundleAndRuntimeIdempotently() = withHarness { harness ->
        assertEquals(
            "function",
            harness.evaluate("typeof SecondPassColibrio.EpubCfiParser.parse").jsonString()
        )
        assertEquals(
            "1.11.0",
            harness.evaluate("__secondPassEpubCfi.runtimeVersion()").jsonString()
        )

        harness.evaluate(asset("reader/cfi/secondpass-epub-cfi-runtime.js"))

        assertEquals(
            "1.11.0",
            harness.evaluate("__secondPassEpubCfi.runtimeVersion()").jsonString()
        )
    }

    @Test
    fun generatesParsesAndResolvesPointAndRangeCfis() = withHarness { harness ->
        val packageCfi = harness.packageCfi()
        val pointContent =
            harness.generateContentCfi(
                """
                const node = document.querySelector("#repeated-phrase").firstChild;
                const offset = node.data.indexOf("repeated phrase");
                builder.appendTerminalDomPosition(node, offset);
                """.trimIndent()
            )
        val pointCfi = harness.compose(packageCfi, pointContent)
        val parsedPoint = successObject(harness.runtime("parse", pointCfi))
        assertEquals("point", parsedPoint.getString("kind"))
        assertTrue(parsedPoint.getBoolean("hasIndirection"))

        val resolvedPoint = harness.resolve(pointCfi)
        assertEquals("point", resolvedPoint.getString("kind"))
        assertTrue(
            resolvedPoint.getJSONObject(
                "movementAnchor"
            ).getString("exact").startsWith("repeated phrase")
        )

        harness.selectNestedInlineRange()
        val selection = successObject(harness.runtime("generateSelectionContentCfi"))
        val rangeCfi = harness.compose(packageCfi, selection.getString("contentCfi"))
        val parsedRange = successObject(harness.runtime("parse", rangeCfi))
        assertEquals("range", parsedRange.getString("kind"))

        val resolvedRange = harness.resolve(rangeCfi)
        assertEquals("nested inline", resolvedRange.getString("selectedText"))
        assertEquals(selection.nullableString("prefix"), resolvedRange.nullableString("prefix"))
        assertEquals(selection.nullableString("suffix"), resolvedRange.nullableString("suffix"))
    }

    @Test
    fun generatesSelectionsAcrossSingleAndNestedTextNodesWithBoundedContext() =
        withHarness { harness ->
            harness.evaluate(
                """
                (() => {
                  const node = document.querySelector("#repeated-phrase").firstChild;
                  const start = node.data.indexOf("repeated phrase");
                  const range = document.createRange();
                  range.setStart(node, start);
                  range.setEnd(node, start + "repeated phrase".length);
                  const selection = window.getSelection();
                  selection.removeAllRanges();
                  selection.addRange(range);
                })()
                """.trimIndent()
            )
            val single = successObject(harness.runtime("generateSelectionContentCfi"))
            assertEquals("repeated phrase", single.getString("selectedText"))
            assertTrue(single.getString("prefix").length <= 64)
            assertTrue(single.getString("suffix").length <= 64)

            harness.selectNestedInlineRange()
            val nested = successObject(harness.runtime("generateSelectionContentCfi"))
            assertEquals("nested inline", nested.getString("selectedText"))
            assertTrue(nested.getString("prefix").endsWith("Before "))
            assertTrue(nested.getString("suffix").startsWith(" markup"))
        }

    @Test
    fun preservesUtf16OffsetsWithoutSplittingSurrogatePairs() = withHarness { harness ->
        harness.evaluate(
            """
            (() => {
              const node = document.querySelector("#unicode-content").firstChild;
              const start = node.data.indexOf("\ud83d\ude00");
              const range = document.createRange();
              range.setStart(node, start);
              range.setEnd(node, start + 2);
              const selection = window.getSelection();
              selection.removeAllRanges();
              selection.addRange(range);
            })()
            """.trimIndent()
        )
        val selection = successObject(harness.runtime("generateSelectionContentCfi"))
        assertEquals("\ud83d\ude00", selection.getString("selectedText"))

        val fullCfi = harness.compose(harness.packageCfi(), selection.getString("contentCfi"))
        assertEquals("\ud83d\ude00", harness.resolve(fullCfi).getString("selectedText"))

        val splitSurrogate =
            harness.evaluateJson(
                """
                (() => {
                  const node = document.querySelector("#unicode-content").firstChild;
                  const start = node.data.indexOf("\ud83d\ude00");
                  const range = document.createRange();
                  range.setStart(node, start + 1);
                  range.setEnd(node, start + 2);
                  const selection = window.getSelection();
                  selection.removeAllRanges();
                  selection.addRange(range);
                  return window.__secondPassEpubCfi.generateSelectionContentCfi();
                })()
                """.trimIndent()
            )
        assertFailure(splitSurrogate, "INVALID_RANGE")
    }

    @Test
    fun excludesConcreteReadiumRuntimeNodesButKeepsPublisherLookalikes() = withHarness { harness ->
        val packageCfi = harness.packageCfi()
        harness.selectText("#post-lookalike-target", 0, 24)
        val before = successObject(harness.runtime("generateSelectionContentCfi"))

        harness.evaluate(SyntheticEpubCfiSources.injectRuntimeDecorationScript)
        harness.selectText("#post-lookalike-target", 0, 24)

        val after = successObject(harness.runtime("generateSelectionContentCfi"))
        assertEquals(before.getString("contentCfi"), after.getString("contentCfi"))
        val fullCfi = harness.compose(packageCfi, after.getString("contentCfi"))
        val resolution = harness.resolve(fullCfi)
        assertTrue(
            resolution.getJSONObject("movementAnchor").getString("exact")
                .startsWith("Publisher content after")
        )

        harness.selectPhrase("#r2-decoration-42", "Publisher content that")
        val publisherSelection = successObject(harness.runtime("generateSelectionContentCfi"))
        assertNotEquals(after.getString("contentCfi"), publisherSelection.getString("contentCfi"))
        val publisherLookalike = harness.compose(
            packageCfi,
            publisherSelection.getString("contentCfi")
        )
        assertTrue(
            harness.resolve(publisherLookalike)
                .getJSONObject("movementAnchor")
                .getString("exact")
                .startsWith("Publisher content that")
        )
    }

    @Test
    fun reportsMalformedAndUnsupportedCfisAsBoundedFailures() = withHarness { harness ->
        assertFailure(harness.runtime("parse", "not-a-cfi"), "INVALID_CFI")

        val unsupported = "epubcfi(/6/2[spine-chapter-one]!/4/2[chapter-one-root])"
        assertFailure(
            harness.runtime(
                "resolvePackage",
                unsupported,
                SyntheticEpubCfiSources.packageDocument,
                SyntheticEpubCfiSources.PACKAGE_PATH
            ),
            "UNSUPPORTED_CFI_FEATURE"
        )
    }

    @Test
    fun selectionRoundTripPreservesSemanticTextContext() = withHarness { harness ->
        harness.selectNestedInlineRange()
        val selection = successObject(harness.runtime("generateSelectionContentCfi"))
        val fullCfi = harness.compose(harness.packageCfi(), selection.getString("contentCfi"))
        val resolution = harness.resolve(fullCfi)

        val verification =
            successObject(
                harness.runtime(
                    "verifyContentTarget",
                    fullCfi,
                    SyntheticEpubCfiSources.packageDocument,
                    SyntheticEpubCfiSources.PACKAGE_PATH,
                    0,
                    "chapter-one",
                    "spine-chapter-one",
                    SyntheticEpubCfiSources.CHAPTER_ONE_PATH,
                    "range",
                    resolution.nullableString("selectedText"),
                    resolution.nullableString("prefix"),
                    resolution.nullableString("suffix"),
                    resolution.getJSONObject("movementAnchor").nullableString("exact"),
                    resolution.getJSONObject("movementAnchor").nullableString("before"),
                    resolution.getJSONObject("movementAnchor").nullableString("after")
                )
            )
        assertTrue(verification.getBoolean("semanticMatch"))
    }

    private fun withHarness(block: suspend (EpubCfiWebViewHarness) -> Unit) = runBlocking {
        val harness = EpubCfiWebViewHarness.create(compose.activity)
        try {
            block(harness)
        } finally {
            harness.close()
        }
    }

    private fun asset(path: String): String =
        compose.activity.assets.open(path).bufferedReader().use { it.readText() }
}

private suspend fun EpubCfiWebViewHarness.packageCfi(): String = successString(
    runtime(
        "generatePackage",
        SyntheticEpubCfiSources.packageDocument,
        SyntheticEpubCfiSources.PACKAGE_PATH,
        0,
        "chapter-one",
        "spine-chapter-one"
    )
)

private suspend fun EpubCfiWebViewHarness.generateContentCfi(body: String): String = evaluate(
    """
        (() => {
          const builder = new SecondPassColibrio.EpubCfiBuilder();
          $body
          return builder.toString();
        })()
    """.trimIndent()
).jsonString()

private suspend fun EpubCfiWebViewHarness.compose(packageCfi: String, contentCfi: String): String =
    successString(runtime("composeFullCfi", packageCfi, contentCfi))

private suspend fun EpubCfiWebViewHarness.resolve(fullCfi: String): JSONObject = successObject(
    runtime(
        "resolveContent",
        fullCfi,
        SyntheticEpubCfiSources.packageDocument,
        SyntheticEpubCfiSources.PACKAGE_PATH,
        0,
        "chapter-one",
        "spine-chapter-one",
        SyntheticEpubCfiSources.CHAPTER_ONE_PATH
    )
)

private suspend fun EpubCfiWebViewHarness.pointCfiFor(
    selector: String,
    packageCfi: String
): String {
    val content =
        generateContentCfi(
            """
            const node = document.querySelector(${JSONObject.quote(selector)}).firstChild;
            builder.appendTerminalDomPosition(node, 0);
            """.trimIndent()
        )
    return compose(packageCfi, content)
}

private suspend fun EpubCfiWebViewHarness.selectNestedInlineRange() {
    evaluate(
        """
        (() => {
          const start = document.querySelector("#nested-span").firstChild;
          const end = document.querySelector("#nested-emphasis").firstChild;
          const range = document.createRange();
          range.setStart(start, 0);
          range.setEnd(end, end.length);
          const selection = window.getSelection();
          selection.removeAllRanges();
          selection.addRange(range);
        })()
        """.trimIndent()
    )
}

private fun successObject(result: JSONObject): JSONObject {
    assertTrue(result.toString(), result.getBoolean("ok"))
    return result.getJSONObject("value")
}

private suspend fun EpubCfiWebViewHarness.selectText(selector: String, start: Int, end: Int) {
    evaluate(
        """
        (() => {
          const node = document.querySelector(${JSONObject.quote(selector)}).firstChild;
          const range = document.createRange();
          range.setStart(node, $start);
          range.setEnd(node, $end);
          const selection = window.getSelection();
          selection.removeAllRanges();
          selection.addRange(range);
        })()
        """.trimIndent()
    )
}

private suspend fun EpubCfiWebViewHarness.selectPhrase(selector: String, phrase: String) {
    evaluate(
        """
        (() => {
          const node = document.querySelector(${JSONObject.quote(selector)}).firstChild;
          const phrase = ${JSONObject.quote(phrase)};
          const start = node.data.indexOf(phrase);
          const range = document.createRange();
          range.setStart(node, start);
          range.setEnd(node, start + phrase.length);
          const selection = window.getSelection();
          selection.removeAllRanges();
          selection.addRange(range);
        })()
        """.trimIndent()
    )
}

private fun successString(result: JSONObject): String {
    assertTrue(result.toString(), result.getBoolean("ok"))
    return result.getString("value")
}

private fun assertFailure(result: JSONObject, expectedCode: String) {
    assertFalse(result.toString(), result.getBoolean("ok"))
    assertEquals(expectedCode, result.getJSONObject("error").getString("code"))
}

private fun JSONObject.nullableString(name: String): String? =
    if (isNull(name)) null else getString(name)

private fun String.jsonString(): String = JSONTokener(this).nextValue() as String
