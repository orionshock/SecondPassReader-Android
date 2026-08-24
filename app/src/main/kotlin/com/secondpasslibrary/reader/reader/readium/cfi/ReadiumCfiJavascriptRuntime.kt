package com.secondpasslibrary.reader.reader.readium.cfi

import android.content.Context
import org.readium.r2.navigator.epub.EpubNavigatorFragment

private const val RUNTIME_VERSION = "1.0.0"
private const val COLIBRIO_ASSET = "reader/cfi/colibrio-epubcfi-1.1.0.min.js"
private const val RUNTIME_ASSET = "reader/cfi/secondpass-epub-cfi-runtime.js"

internal class ReadiumCfiJavascriptRuntime(context: Context) {
    private val applicationContext = context.applicationContext
    private val installationScript by lazy {
        listOf(COLIBRIO_ASSET, RUNTIME_ASSET).joinToString(separator = "\n") { path ->
            applicationContext.assets.open(path).bufferedReader().use { it.readText() }
        }
    }

    suspend fun ensureInstalled(navigator: EpubNavigatorFragment): Boolean {
        if (installedVersion(navigator) == RUNTIME_VERSION) return true
        val installed = navigator.evaluateJavascript(
            "$installationScript\nwindow.__secondPassEpubCfi.runtimeVersion();"
        )
        return installed.decodeJavascriptString() == RUNTIME_VERSION
    }

    private suspend fun installedVersion(navigator: EpubNavigatorFragment): String? =
        navigator.evaluateJavascript(
            "window.__secondPassEpubCfi && " +
                "window.__secondPassEpubCfi.runtimeVersion();"
        ).decodeJavascriptString()
}

private fun String?.decodeJavascriptString(): String? = this
    ?.takeUnless { it == "null" || it == "undefined" }
    ?.removeSurrounding("\"")
