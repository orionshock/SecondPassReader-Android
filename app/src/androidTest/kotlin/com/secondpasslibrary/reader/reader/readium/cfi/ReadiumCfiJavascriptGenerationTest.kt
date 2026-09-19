package com.secondpasslibrary.reader.reader.readium.cfi

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.reader.reader.cfi.SyntheticEpubCfiSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ReadiumCfiJavascriptGenerationTest : ReadiumCfiJavascriptRuntimeTestSupport() {
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
        assertCompactDurableCfi(selection.getString("contentCfi"))
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
            assertCompactDurableCfi(single.getString("contentCfi"))
            assertTrue(single.getString("prefix").length <= 2_000)
            assertTrue(single.getString("suffix").length <= 2_000)

            harness.selectNestedInlineRange()
            val nested = successObject(harness.runtime("generateSelectionContentCfi"))
            assertEquals("nested inline", nested.getString("selectedText"))
            assertCompactDurableCfi(nested.getString("contentCfi"))
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
        assertCompactDurableCfi(selection.getString("contentCfi"))
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
}
