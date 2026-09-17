package com.secondpasslibrary.reader.reader.readium.cfi

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.reader.reader.cfi.SyntheticEpubCfiSources
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ReadiumCfiJavascriptVisibilityTest : ReadiumCfiJavascriptRuntimeTestSupport() {
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
}
