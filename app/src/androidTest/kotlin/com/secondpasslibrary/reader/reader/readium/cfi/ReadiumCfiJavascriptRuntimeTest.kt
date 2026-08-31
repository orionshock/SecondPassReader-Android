package com.secondpasslibrary.reader.reader.readium.cfi

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.reader.reader.cfi.SyntheticEpubCfiSources
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadiumCfiJavascriptRuntimeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun mapsMissingPackageItemrefIdAsNull() {
        val target = readPackageTarget(
            JSONObject(
                """{"spineIndex":28,"itemrefId":null,"idref":"id97","kind":"range"}"""
            )
        )

        assertNull(target.itemrefId)
    }

    @Test
    fun installsPinnedBundleAndRuntimeIdempotently() = withHarness { harness ->
        assertEquals(
            "function",
            harness.evaluate("typeof SecondPassColibrio.EpubCfiParser.parse").jsonString()
        )
        assertEquals(
            "1.12.9",
            harness.evaluate("__secondPassEpubCfi.runtimeVersion()").jsonString()
        )

        harness.evaluate(asset("reader/cfi/secondpass-epub-cfi-runtime.js"))

        assertEquals(
            "1.12.9",
            harness.evaluate("__secondPassEpubCfi.runtimeVersion()").jsonString()
        )
    }

    @Test
    fun documentReadinessRequiresReadiumPublicationRuntime() = withHarness { harness ->
        assertTrue(successBoolean(harness.runtime("isDocumentReady")))

        harness.evaluate("delete window.readium")

        assertFalse(successBoolean(harness.runtime("isDocumentReady")))

        harness.evaluate("window.readium = { isReflowable: false, isFixedLayout: true }")

        assertFailure(harness.runtime("isDocumentReady"), "UNSUPPORTED_FIXED_LAYOUT")
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
            assertTrue(single.getString("prefix").length <= 2_000)
            assertTrue(single.getString("suffix").length <= 2_000)

            harness.selectNestedInlineRange()
            val nested = successObject(harness.runtime("generateSelectionContentCfi"))
            assertEquals("nested inline", nested.getString("selectedText"))
            assertTrue(nested.getString("prefix").endsWith("Before "))
            assertTrue(nested.getString("suffix").startsWith(" markup"))
        }

    @Test
    fun selectionCaptureProvidesRawContextForMutationBudgeting() = withHarness { harness ->
        harness.evaluate(
            """
            (() => {
              document.body.innerHTML = '<p id="context"></p>';
              const node = document.getElementById("context");
              node.textContent = "p".repeat(2500) + "selected" + "s".repeat(2500);
              const text = node.firstChild;
              const range = document.createRange();
              range.setStart(text, 2500);
              range.setEnd(text, 2508);
              const selection = window.getSelection();
              selection.removeAllRanges();
              selection.addRange(range);
            })()
            """.trimIndent()
        )

        val selection = successObject(harness.runtime("generateSelectionContentCfi"))

        assertEquals("selected", selection.getString("selectedText"))
        assertEquals("p".repeat(2_000), selection.getString("prefix"))
        assertEquals("s".repeat(2_000), selection.getString("suffix"))

        val fullCfi = harness.compose(harness.packageCfi(), selection.getString("contentCfi"))
        val resolution = harness.resolve(fullCfi)
        assertEquals(selection.getString("selectedText"), resolution.getString("selectedText"))
        assertEquals(selection.getString("prefix"), resolution.getString("prefix"))
        assertEquals(selection.getString("suffix"), resolution.getString("suffix"))
        assertTrue(resolution.getJSONObject("movementAnchor").getString("before").length <= 64)
        assertTrue(resolution.getJSONObject("movementAnchor").getString("after").length <= 64)
    }

    @Test
    fun selectionAndResolutionShareRawContextAcrossDomBoundaries() = withHarness { harness ->
        val vectors = listOf(
            "same text node" to
                """
                document.body.innerHTML = '<p id="root">before selected after</p>';
                const text = document.getElementById('root').firstChild;
                range.setStart(text, 7);
                range.setEnd(text, 15);
                """.trimIndent(),
            "inline markup" to
                """
                document.body.innerHTML =
                  '<p>before <em id="start">cross</em><span id="end"> markup</span> after</p>';
                range.setStart(document.getElementById('start').firstChild, 2);
                range.setEnd(document.getElementById('end').firstChild, 4);
                """.trimIndent(),
            "text-node edges" to
                """
                document.body.innerHTML = '<p>before <span id="exact">selected</span> after</p>';
                const text = document.getElementById('exact').firstChild;
                range.setStart(text, 0);
                range.setEnd(text, text.length);
                """.trimIndent(),
            "paragraph boundary" to
                """
                document.body.innerHTML = '<p id="start">before tail</p><p id="end">head after</p>';
                range.setStart(document.getElementById('start').firstChild, 7);
                range.setEnd(document.getElementById('end').firstChild, 4);
                """.trimIndent(),
            "raw whitespace" to
                """
                document.body.innerHTML =
                  '<p id="root">before&nbsp;  \t\nselected\r\n  after&nbsp;text</p>';
                const text = document.getElementById('root').firstChild;
                const start = text.data.indexOf('selected');
                range.setStart(text, start);
                range.setEnd(text, start + 'selected'.length);
                """.trimIndent(),
            "transient neighbor" to
                """
                document.body.innerHTML =
                  '<p>publisher before</p>' +
                  '<div id="r2-decoration-123" data-group="test" style="pointer-events: none">' +
                  'runtime-only context</div>' +
                  '<p id="root">selected publisher after</p>';
                const text = document.getElementById('root').firstChild;
                range.setStart(text, 0);
                range.setEnd(text, 'selected'.length);
                """.trimIndent(),
            "surrogate at context boundary" to
                """
                document.body.innerHTML = '<p id="root"></p>';
                const text = document.getElementById('root');
                text.textContent = '\ud83d\ude00' + 'p'.repeat(1999) + 'selected' + 's'.repeat(2000);
                const node = text.firstChild;
                const start = node.data.indexOf('selected');
                range.setStart(node, start);
                range.setEnd(node, start + 'selected'.length);
                """.trimIndent()
        )

        vectors.forEach { (name, arrangeRange) ->
            harness.evaluate(
                """
                (() => {
                  const range = document.createRange();
                  $arrangeRange
                  const selection = window.getSelection();
                  selection.removeAllRanges();
                  selection.addRange(range);
                })()
                """.trimIndent()
            )
            val selection = successObject(harness.runtime("generateSelectionContentCfi"))
            val fullCfi = harness.compose(
                harness.packageCfi(),
                selection.getString("contentCfi")
            )
            val resolution = harness.resolve(fullCfi)

            assertEquals(
                "$name exact",
                selection.getString("selectedText"),
                resolution.getString("selectedText")
            )
            assertEquals(
                "$name prefix",
                selection.nullableString("prefix"),
                resolution.nullableString("prefix")
            )
            assertEquals(
                "$name suffix",
                selection.nullableString("suffix"),
                resolution.nullableString("suffix")
            )
        }
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

        val sideBiased =
            "epubcfi(/6/2[spine-chapter-one]!/4/2[chapter-one-root]/4" +
                "[repeated-phrase]/1:4[;s=b])"
        assertFailure(
            harness.runtime(
                "resolvePackage",
                sideBiased,
                SyntheticEpubCfiSources.packageDocument,
                SyntheticEpubCfiSources.PACKAGE_PATH
            ),
            "UNSUPPORTED_CFI_FEATURE"
        )
    }

    @Test
    fun rejectsTerminalPointWithoutTruthfulMovementAnchor() = withHarness { harness ->
        val packageCfi = harness.packageCfi()
        val terminalContent =
            harness.generateContentCfi(
                """
                const node = document.querySelector("#post-lookalike-target").firstChild;
                builder.appendTerminalDomPosition(node, node.length);
                """.trimIndent()
            )

        assertFailure(
            harness.runtime(
                "resolveContent",
                harness.compose(packageCfi, terminalContent),
                SyntheticEpubCfiSources.packageDocument,
                SyntheticEpubCfiSources.PACKAGE_PATH,
                0,
                "chapter-one",
                "spine-chapter-one",
                SyntheticEpubCfiSources.CHAPTER_ONE_PATH
            ),
            "UNSUPPORTED_CFI_FEATURE"
        )
    }

    @Test
    fun rejectsSelectionLargerThanSplHighlightBoundaryWithoutTruncating() = withHarness { harness ->
        harness.evaluate(
            """
                (() => {
                  document.body.innerHTML = '<p id="large-selection"></p>';
                  const node = document.getElementById("large-selection");
                  for (let index = 0; index < 4; index += 1) {
                    const part = document.createElement("span");
                    part.textContent = "x".repeat(16 * 1024);
                    node.appendChild(part);
                  }
                  const range = document.createRange();
                  range.selectNodeContents(node);
                  const selection = window.getSelection();
                  selection.removeAllRanges();
                  selection.addRange(range);
                })()
            """.trimIndent()
        )
        val maximum = successObject(harness.runtime("generateSelectionContentCfi"))
        assertEquals(64 * 1024, maximum.getString("selectedText").length)

        harness.evaluate(
            """
                (() => {
                  const node = document.getElementById("large-selection");
                  node.lastChild.textContent += "x";
                  const range = document.createRange();
                  range.selectNodeContents(node);
                  const selection = window.getSelection();
                  selection.removeAllRanges();
                  selection.addRange(range);
                })()
            """.trimIndent()
        )

        assertFailure(
            harness.runtime("generateSelectionContentCfi"),
            "RESULT_TOO_LARGE"
        )
    }

    @Test
    fun visiblePositionUsesLogicalLeadingTextAcrossPaginatedGeometry() = withHarness { harness ->
        val packageCfi = harness.packageCfi()

        harness.preparePaginatedGeometry("ltr", "LTR leading page text")
        harness.assertCurrentPositionStartsWith(packageCfi, "LTR leading page text")

        harness.preparePaginatedGeometry("rtl", "RTL logical leading text")
        harness.assertCurrentPositionStartsWith(packageCfi, "RTL logical leading text")
    }

    @Test
    fun visiblePositionFindsFirstVisibleCharacterInsidePartiallyClippedText() =
        withHarness { harness ->
            val packageCfi = harness.packageCfi()
            harness.prepareGeometry(
                direction = "ltr",
                body =
                    """
                    <p id="partial" style="position:absolute;left:0;top:8px;
                        margin:0;white-space:pre;font:24px monospace">ABCDLeading clipped text</p>
                    """.trimIndent()
            )
            harness.evaluate(
                """
                (() => {
                  const node = document.getElementById("partial").firstChild;
                  const prefix = document.createRange();
                  prefix.setStart(node, 0);
                  prefix.setEnd(node, 4);
                  document.getElementById("partial").style.left =
                    `${'$'}{-prefix.getBoundingClientRect().width}px`;
                })()
                """.trimIndent()
            )

            harness.assertCurrentPositionStartsWith(packageCfi, "Leading clipped text")
        }

    @Test
    fun visiblePositionSkipsBlankImageZeroWidthAndSubpixelPreviousContent() =
        withHarness { harness ->
            val packageCfi = harness.packageCfi()
            harness.prepareGeometry(
                direction = "ltr",
                body =
                    """
                    <img alt="Cover page" style="position:absolute;left:0;top:0;width:40px;height:40px"/>
                    <p>   &#8203; </p>
                    <p style="position:absolute;left:0;top:48px;transform:scaleX(0);
                        transform-origin:left">Zero width text</p>
                    <p id="subpixel" style="position:absolute;left:0;top:72px;
                        margin:0;white-space:pre;font:20px monospace">Previous column remnant</p>
                    <p style="position:absolute;left:8px;top:104px">
                      <span>Inline</span> leading target
                    </p>
                    """.trimIndent()
            )
            harness.evaluate(
                """
                (() => {
                  const element = document.getElementById("subpixel");
                  const range = document.createRange();
                  range.selectNodeContents(element);
                  element.style.left =
                    `${'$'}{-range.getBoundingClientRect().width + 0.25}px`;
                })()
                """.trimIndent()
            )

            harness.assertCurrentPositionStartsWith(packageCfi, "Inline")
        }

    @Test
    fun pointVisibilityBatchesCandidatesAndUsesTerminalCharacterFallback() =
        withHarness { harness ->
            val packageCfi = harness.packageCfi()
            harness.prepareGeometry(
                direction = "ltr",
                body =
                    """
                    <p id="visible" style="position:absolute;left:8px;top:8px">Visible point</p>
                    <p id="offscreen" style="position:absolute;left:10000px;top:8px">Offscreen</p>
                    <p id="terminal" style="position:absolute;left:8px;top:48px">Terminal point</p>
                    """.trimIndent()
            )
            suspend fun point(selector: String, terminal: Boolean = false): String {
                val content = harness.generateContentCfi(
                    """
                    const node = document.querySelector(${JSONObject.quote(selector)}).firstChild;
                    builder.appendTerminalDomPosition(node, ${if (terminal) "node.length" else "0"});
                    """.trimIndent()
                )
                return harness.compose(packageCfi, content)
            }
            val candidates = JSONArray()
                .put(JSONObject().put("id", "visible").put("cfi", point("#visible")))
                .put(JSONObject().put("id", "offscreen").put("cfi", point("#offscreen")))
                .put(JSONObject().put("id", "terminal").put("cfi", point("#terminal", true)))
                .put(JSONObject().put("id", "malformed").put("cfi", "not-a-cfi"))

            val result = harness.runtime(
                "visiblePointTargets",
                candidates.toString(),
                SyntheticEpubCfiSources.packageDocument,
                SyntheticEpubCfiSources.PACKAGE_PATH,
                0,
                "chapter-one",
                "spine-chapter-one",
                SyntheticEpubCfiSources.CHAPTER_ONE_PATH
            )
            assertTrue(result.toString(), result.getBoolean("ok"))
            val visible = result.getJSONArray("value").let { values ->
                buildSet {
                    repeat(values.length()) { index ->
                        values.getJSONObject(index).takeIf { it.getBoolean("visible") }
                            ?.let { add(it.getString("id")) }
                    }
                }
            }

            assertEquals(result.toString(), setOf("visible", "terminal"), visible)
        }

    @Test
    fun packageCandidateBatchResolvesValidTargetsAndIsolatesMalformedCfi() =
        withHarness { harness ->
            val contentCfi = harness.generateContentCfi(
                """
                const node = document.querySelector("#repeated-phrase").firstChild;
                builder.appendTerminalDomPosition(node, 0);
                """.trimIndent()
            )
            val chapterOne = harness.compose(harness.packageCfi(), contentCfi)
            val chapterTwoPackage = successString(
                harness.runtime(
                    "generatePackage",
                    SyntheticEpubCfiSources.packageDocument,
                    SyntheticEpubCfiSources.PACKAGE_PATH,
                    1,
                    "chapter-two",
                    "spine-chapter-two"
                )
            )
            val chapterTwo = harness.compose(chapterTwoPackage, contentCfi)
            val candidates = JSONArray()
                .put(JSONObject().put("id", "one").put("cfi", chapterOne))
                .put(JSONObject().put("id", "two").put("cfi", chapterTwo))
                .put(JSONObject().put("id", "malformed").put("cfi", "not-a-cfi"))

            val result = harness.runtime(
                "resolvePackageCandidates",
                candidates.toString(),
                SyntheticEpubCfiSources.packageDocument,
                SyntheticEpubCfiSources.PACKAGE_PATH
            )
            assertTrue(result.toString(), result.getBoolean("ok"))
            val resolved = result.getJSONArray("value")

            assertEquals(2, resolved.length())
            assertEquals(
                listOf("one", "two"),
                List(resolved.length()) { index ->
                    resolved.getJSONObject(index).getString("id")
                }
            )
            assertEquals(
                listOf(0, 1),
                List(resolved.length()) { index ->
                    resolved.getJSONObject(index).getInt("spineIndex")
                }
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

private suspend fun EpubCfiWebViewHarness.preparePaginatedGeometry(
    direction: String,
    expectedText: String
) {
    evaluate(
        """
        (() => {
          const pageWidth = document.documentElement.clientWidth;
          const pageHeight = document.documentElement.clientHeight;
          document.documentElement.style.cssText =
            "margin:0;overflow:hidden;writing-mode:horizontal-tb";
          document.body.style.cssText =
            "margin:0;overflow:hidden;direction:$direction";
          document.body.innerHTML =
            '<div id="page-viewport"><main id="columns"><section><p id="previous-page">' +
            'Previous CSS column text</p></section><section><p id="current-page"></p>' +
            '</section><section><p id="next-page">Later CSS column text</p></section></main></div>';
          document.getElementById("current-page").textContent =
            ${JSONObject.quote(expectedText)};
          const viewport = document.getElementById("page-viewport");
          viewport.style.cssText =
            `position:absolute;left:0;top:0;width:${'$'}{pageWidth}px;` +
            `height:${'$'}{pageHeight}px;overflow:hidden;direction:ltr`;
          const columns = document.getElementById("columns");
          columns.style.cssText =
            `position:absolute;left:0;top:0;width:${'$'}{pageWidth}px;` +
            `height:${'$'}{pageHeight}px;column-width:${'$'}{pageWidth}px;` +
            "column-gap:0;column-fill:auto;transform-origin:left top;direction:ltr";
          Array.from(columns.children).forEach((page) => {
            page.style.cssText =
              "box-sizing:border-box;margin:0;padding:8px;overflow:hidden;direction:$direction";
          });
          columns.children[1].style.breakBefore = "column";
          columns.children[2].style.breakBefore = "column";
        })()
        """.trimIndent()
    )
    evaluate(
        """
        (() => {
          const pageWidth = document.documentElement.clientWidth;
          const columns = document.getElementById("columns");
          const textRectangles = (id) => {
            const range = document.createRange();
            range.selectNodeContents(document.getElementById(id).firstChild);
            return Array.from(range.getClientRects());
          };
          const currentBefore = textRectangles("current-page")[0];
          const targetScroll = "$direction" === "rtl"
            ? currentBefore.right - (pageWidth - 8)
            : currentBefore.left - 8;
          const viewport = document.getElementById("page-viewport");
          viewport.scrollLeft = targetScroll;
          window.__secondPassPaginationTest = {
            currentBeforeLeft: currentBefore.left,
            currentBeforeRight: currentBefore.right,
            targetScroll: targetScroll,
            appliedScroll: viewport.scrollLeft,
            scrollWidth: viewport.scrollWidth
          };
        })()
        """.trimIndent()
    )
    val geometry = evaluateJson(
        """
        (() => {
          const pageWidth = document.documentElement.clientWidth;
          const pageHeight = document.documentElement.clientHeight;
          const columns = document.getElementById("columns");
          const textRectangles = (id) => {
            const range = document.createRange();
            range.selectNodeContents(document.getElementById(id).firstChild);
            return Array.from(range.getClientRects());
          };
          const currentAfter = textRectangles("current-page");
          const previousAfter = textRectangles("previous-page");
          const nextAfter = textRectangles("next-page");
          const intersectsViewport = (rectangle) =>
            rectangle.right > 0.5 && rectangle.left < pageWidth - 0.5 &&
            rectangle.bottom > 0.5 && rectangle.top < pageHeight - 0.5;
          const isolated = Math.abs(parseFloat(columns.style.columnWidth) - pageWidth) <= 1 &&
            currentAfter.some(intersectsViewport) &&
            !previousAfter.some(intersectsViewport) &&
            !nextAfter.some(intersectsViewport);
          const coordinates = (rectangles) => rectangles.map((rectangle) => [
            rectangle.left,
            rectangle.right,
            rectangle.top,
            rectangle.bottom
          ]);
          return {
            isolated: isolated,
            pageWidth: pageWidth,
            columnWidth: columns.style.columnWidth,
            placement: window.__secondPassPaginationTest,
            previous: coordinates(previousAfter),
            current: coordinates(currentAfter),
            next: coordinates(nextAfter)
          };
        })()
        """.trimIndent()
    )
    assertTrue(geometry.toString(), geometry.getBoolean("isolated"))
}

private suspend fun EpubCfiWebViewHarness.prepareGeometry(direction: String, body: String) {
    evaluate(
        """
        (() => {
          document.documentElement.style.cssText =
            "margin:0;overflow:hidden;writing-mode:horizontal-tb";
          document.body.style.cssText =
            "margin:0;overflow:hidden;direction:$direction";
          document.body.innerHTML = ${JSONObject.quote(body)};
        })()
        """.trimIndent()
    )
}

private suspend fun EpubCfiWebViewHarness.assertCurrentPositionStartsWith(
    packageCfi: String,
    expectedPrefix: String
) {
    val contentCfi = successString(runtime("generateVisiblePositionContentCfi"))
    val resolution = resolve(compose(packageCfi, contentCfi))
    val exact = resolution.getJSONObject("movementAnchor").getString("exact")
    assertTrue(
        "Expected '$expectedPrefix' at the viewport lead, got '$exact' from $contentCfi",
        exact.startsWith(expectedPrefix)
    )
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

private fun successBoolean(result: JSONObject): Boolean {
    assertTrue(result.toString(), result.getBoolean("ok"))
    return result.getBoolean("value")
}

private fun assertFailure(result: JSONObject, expectedCode: String) {
    assertFalse(result.toString(), result.getBoolean("ok"))
    assertEquals(expectedCode, result.getJSONObject("error").getString("code"))
}

private fun JSONObject.nullableString(name: String): String? =
    if (isNull(name)) null else getString(name)

private fun String.jsonString(): String = JSONTokener(this).nextValue() as String
