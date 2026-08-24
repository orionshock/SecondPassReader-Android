package com.secondpasslibrary.reader.reader.readium.cfi

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import com.secondpasslibrary.reader.reader.cfi.SyntheticEpubCfiSources
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.json.JSONTokener

internal class EpubCfiWebViewHarness private constructor(
    private val activity: ComponentActivity,
    private val webView: WebView
) {
    suspend fun evaluate(script: String): String {
        val result = CompletableDeferred<String>()
        activity.runOnUiThread {
            webView.evaluateJavascript(script) { value -> result.complete(value) }
        }
        return withTimeout(JAVASCRIPT_TIMEOUT_MILLIS) { result.await() }
    }

    suspend fun evaluateJson(expression: String): JSONObject {
        val encoded = evaluate("JSON.stringify($expression)")
        val decoded = JSONTokener(encoded).nextValue() as String
        return JSONObject(decoded)
    }

    suspend fun runtime(method: String, vararg arguments: Any?): JSONObject = evaluateJson(
        buildString {
            append("window.__secondPassEpubCfi.")
            append(method)
            append('(')
            arguments.joinTo(this) { argument -> serializeJavascriptArgument(argument) }
            append(')')
        }
    )

    suspend fun close() {
        val closed = CompletableDeferred<Unit>()
        activity.runOnUiThread {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
            closed.complete(Unit)
        }
        withTimeout(WEBVIEW_TIMEOUT_MILLIS) { closed.await() }
    }

    companion object {
        private const val WEBVIEW_TIMEOUT_MILLIS = 10_000L
        private const val JAVASCRIPT_TIMEOUT_MILLIS = 10_000L

        @SuppressLint("SetJavaScriptEnabled")
        suspend fun create(activity: ComponentActivity): EpubCfiWebViewHarness {
            val pageLoaded = CompletableDeferred<Unit>()
            val webViewReady = CompletableDeferred<WebView>()
            activity.runOnUiThread {
                val webView =
                    WebView(activity).apply {
                        settings.javaScriptEnabled = true
                        webViewClient =
                            object : WebViewClient() {
                                override fun onPageFinished(view: WebView, url: String) {
                                    pageLoaded.complete(Unit)
                                }
                            }
                    }
                activity.setContentView(webView)
                webView.loadDataWithBaseURL(
                    CHAPTER_ONE_URL,
                    SyntheticEpubCfiSources.chapterOneXhtml,
                    "application/xhtml+xml",
                    Charsets.UTF_8.name(),
                    null
                )
                webViewReady.complete(webView)
            }

            val webView = withTimeout(WEBVIEW_TIMEOUT_MILLIS) { webViewReady.await() }
            withTimeout(WEBVIEW_TIMEOUT_MILLIS) { pageLoaded.await() }
            val harness = EpubCfiWebViewHarness(activity, webView)
            harness.installRuntime()
            return harness
        }

        private suspend fun EpubCfiWebViewHarness.installRuntime() {
            val assets = activity.assets
            val colibrio =
                assets.open("reader/cfi/colibrio-epubcfi-1.1.0.min.js")
                    .bufferedReader()
                    .use { it.readText() }
            val secondPassRuntime =
                assets.open("reader/cfi/secondpass-epub-cfi-runtime.js")
                    .bufferedReader()
                    .use { it.readText() }
            evaluate(colibrio)
            evaluate(secondPassRuntime)
            evaluate(
                """
                (() => {
                  window.readium = { isReflowable: true, isFixedLayout: false };
                  document.documentElement.style.writingMode = "horizontal-tb";
                  document.body.style.direction = "ltr";
                })()
                """.trimIndent()
            )
        }
    }
}

internal const val CHAPTER_ONE_URL =
    "https://secondpass.invalid/${SyntheticEpubCfiSources.CHAPTER_ONE_PATH}"

private fun serializeJavascriptArgument(argument: Any?): String = when (argument) {
    null -> "null"
    is String -> JSONObject.quote(argument)
    is Number, is Boolean -> argument.toString()
    else -> error("Unsupported JavaScript argument: ${argument::class.java.name}")
}
