package com.secondpasslibrary.reader.reader.readium.annotations

import android.webkit.JavascriptInterface
import com.secondpasslibrary.reader.reader.cfi.EpubCfiReadiness
import com.secondpasslibrary.reader.reader.readium.cfi.ReadiumCfiNavigatorBinding
import com.secondpasslibrary.reader.reader.readium.cfi.ReadiumCfiResourceIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.readium.r2.navigator.epub.EpubNavigatorFragment

/** Installs one generation-scoped DOM selection observer in each live publication document. */
internal class ReadiumSelectionDocumentObserver(
    private val cfiBinding: ReadiumCfiNavigatorBinding,
    private val onSelectionChanged: () -> Unit
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val javascriptBridge = SelectionJavascriptBridge(::documentSelectionChanged)
    private var navigator: EpubNavigatorFragment? = null
    private var readinessJob: Job? = null

    @Volatile
    private var activeDocumentToken: String? = null

    fun bind(next: EpubNavigatorFragment) {
        if (navigator === next) return
        navigator?.let(::unbind)
        navigator = next
        readinessJob = scope.launch {
            cfiBinding.readiness.collectLatest { readiness ->
                activeDocumentToken = null
                if (readiness == EpubCfiReadiness.Available && navigator === next) {
                    installDocumentObserver(next)
                }
            }
        }
    }

    fun unbind(current: EpubNavigatorFragment) {
        if (navigator !== current) return
        activeDocumentToken = null
        readinessJob?.cancel()
        readinessJob = null
        navigator = null
    }

    /** Registered through Readium's public per-resource JavaScript-interface configuration. */
    fun javascriptInterface(): Any = javascriptBridge

    override fun close() {
        navigator?.let(::unbind)
        scope.cancel()
    }

    private suspend fun installDocumentObserver(expectedNavigator: EpubNavigatorFragment) {
        cfiBinding.withNavigator { bound, _ ->
            if (bound !== expectedNavigator || navigator !== expectedNavigator) return@withNavigator
            val identity = cfiBinding.resourceIdentity(bound) ?: return@withNavigator
            installObserver(bound, identity)
        }
    }

    private suspend fun installObserver(
        bound: EpubNavigatorFragment,
        identity: ReadiumCfiResourceIdentity
    ) {
        val token = "${identity.navigatorGeneration}:${identity.resourceGeneration}"
        activeDocumentToken = token
        val installed = evaluateObserverInstallation(bound, token)
        val stillCurrent = cfiBinding.resourceIdentity(bound) == identity
        if (!installed || !stillCurrent) clearActiveToken(token)
    }

    private suspend fun evaluateObserverInstallation(
        navigator: EpubNavigatorFragment,
        token: String
    ): Boolean = try {
        withTimeoutOrNull(OBSERVER_INSTALL_TIMEOUT_MILLIS) {
            navigator.evaluateJavascript(selectionObserverScript(token))
        } != null
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }

    private fun clearActiveToken(token: String) {
        if (activeDocumentToken == token) activeDocumentToken = null
    }

    private fun documentSelectionChanged(token: String) {
        if (token.length <= MAX_DOCUMENT_TOKEN_LENGTH && token == activeDocumentToken) {
            onSelectionChanged()
        }
    }

    private companion object {
        const val MAX_DOCUMENT_TOKEN_LENGTH = 64
        const val OBSERVER_INSTALL_TIMEOUT_MILLIS = 5_000L
    }
}

internal const val SELECTION_JAVASCRIPT_INTERFACE = "secondPassSelectionEvents"

private class SelectionJavascriptBridge(private val selectionChanged: (String) -> Unit) {
    @JavascriptInterface
    fun changed(documentToken: String) {
        selectionChanged(documentToken)
    }
}

private fun selectionObserverScript(documentToken: String): String =
    """
    (() => {
      const bridge = window.$SELECTION_JAVASCRIPT_INTERFACE;
      if (!bridge || typeof bridge.changed !== "function") return false;
      const key = "__secondPassSelectionObserver";
      const previous = window[key];
      if (previous && previous.token === "$documentToken") return true;
      if (previous && previous.listener) {
        document.removeEventListener("selectionchange", previous.listener);
      }
      const listener = () => bridge.changed("$documentToken");
      window[key] = { token: "$documentToken", listener: listener };
      document.addEventListener("selectionchange", listener);
      listener();
      return true;
    })();
    """.trimIndent()
