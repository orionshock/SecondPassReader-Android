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

internal abstract class ReadiumCfiJavascriptRuntimeTestSupport {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    protected fun withHarness(block: suspend (EpubCfiWebViewHarness) -> Unit) = runBlocking {
        val harness = EpubCfiWebViewHarness.create(compose.activity)
        try {
            block(harness)
        } finally {
            harness.close()
        }
    }

    protected fun asset(path: String): String =
        compose.activity.assets.open(path).bufferedReader().use { it.readText() }
}

internal suspend fun EpubCfiWebViewHarness.packageCfi(): String = successString(
    runtime(
        "generatePackage",
        SyntheticEpubCfiSources.packageDocument,
        SyntheticEpubCfiSources.PACKAGE_PATH,
        0,
        "chapter-one",
        "spine-chapter-one"
    )
)

internal suspend fun EpubCfiWebViewHarness.generateContentCfi(body: String): String = evaluate(
    """
        (() => {
          const builder = new SecondPassColibrio.EpubCfiBuilder();
          $body
          return builder.toString();
        })()
    """.trimIndent()
).jsonString()

internal suspend fun EpubCfiWebViewHarness.compose(packageCfi: String, contentCfi: String): String =
    successString(runtime("composeFullCfi", packageCfi, contentCfi))

internal suspend fun EpubCfiWebViewHarness.resolve(fullCfi: String): JSONObject = successObject(
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

internal suspend fun EpubCfiWebViewHarness.pointCfiFor(
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

internal suspend fun EpubCfiWebViewHarness.preparePaginatedGeometry(
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

internal suspend fun EpubCfiWebViewHarness.prepareGeometry(direction: String, body: String) {
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

internal suspend fun EpubCfiWebViewHarness.assertCurrentPositionStartsWith(
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

internal suspend fun EpubCfiWebViewHarness.selectNestedInlineRange() {
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

internal fun successObject(result: JSONObject): JSONObject {
    assertTrue(result.toString(), result.getBoolean("ok"))
    return result.getJSONObject("value")
}

internal suspend fun EpubCfiWebViewHarness.selectText(selector: String, start: Int, end: Int) {
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

internal suspend fun EpubCfiWebViewHarness.selectPhrase(selector: String, phrase: String) {
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

internal fun successString(result: JSONObject): String {
    assertTrue(result.toString(), result.getBoolean("ok"))
    return result.getString("value")
}

internal fun successBoolean(result: JSONObject): Boolean {
    assertTrue(result.toString(), result.getBoolean("ok"))
    return result.getBoolean("value")
}

internal fun assertFailure(result: JSONObject, expectedCode: String) {
    assertFalse(result.toString(), result.getBoolean("ok"))
    assertEquals(expectedCode, result.getJSONObject("error").getString("code"))
}

internal fun JSONObject.nullableString(name: String): String? =
    if (isNull(name)) null else getString(name)

internal fun String.jsonString(): String = JSONTokener(this).nextValue() as String
