package com.secondpasslibrary.reader.reader.readium.cfi

import android.content.Context
import com.secondpasslibrary.reader.reader.cfi.EpubCfiFailure
import com.secondpasslibrary.reader.reader.readium.cfi.CfiProtocol as P
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONTokener
import org.readium.r2.navigator.epub.EpubNavigatorFragment

private const val MAX_RUNTIME_ENVELOPE_LENGTH = 256 * 1024
private const val MAX_EVALUATED_RESULT_LENGTH = 512 * 1024
private const val JAVASCRIPT_CALL_TIMEOUT_MILLIS = 10_000L
private const val JAVASCRIPT_INSTALL_TIMEOUT_MILLIS = 20_000L
private const val COLIBRIO_ASSET = "reader/cfi/colibrio-epubcfi-1.1.0.min.js"
private const val RUNTIME_ASSET = "reader/cfi/secondpass-epub-cfi-runtime.js"

/** Owns runtime installation, bounded WebView evaluation, and result-envelope decoding. */
internal class ReadiumCfiJavascriptBridge(private val context: Context) {
    private val installationScript by lazy {
        listOf(COLIBRIO_ASSET, RUNTIME_ASSET).joinToString(separator = "\n") { path ->
            context.assets.open(path).bufferedReader().use { it.readText() }
        }
    }

    suspend fun installationFailure(navigator: EpubNavigatorFragment): EpubCfiFailure? = try {
        if (ensureInstalledWithinDeadline(navigator)) {
            null
        } else {
            EpubCfiFailure.JAVASCRIPT_RUNTIME_UNAVAILABLE
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: JavascriptRuntimeTimeoutException) {
        EpubCfiFailure.JAVASCRIPT_RUNTIME_TIMEOUT
    } catch (_: JavascriptResultTooLargeException) {
        EpubCfiFailure.JAVASCRIPT_RESULT_TOO_LARGE
    } catch (_: Exception) {
        EpubCfiFailure.JAVASCRIPT_RUNTIME_UNAVAILABLE
    }

    suspend fun invoke(
        navigator: EpubNavigatorFragment,
        method: CfiRuntimeMethod,
        arguments: Map<String, JavascriptArgument>
    ): ReadiumCfiJavascriptResult<Any?> {
        require(arguments.keys == method.argumentNames.toSet()) {
            "CFI runtime arguments do not match the protocol."
        }
        val serializedArguments = method.argumentNames.map(arguments::getValue).joinToString(
            ",",
            transform = JavascriptArgument::source
        )
        val script = boundedInvocationScript(method, serializedArguments)
        val evaluation: ReadiumCfiJavascriptResult<JSONObject?> = try {
            ReadiumCfiJavascriptResult.Success(
                if (ensureInstalledWithinDeadline(navigator)) {
                    evaluateEnvelope(navigator, script)
                } else {
                    null
                }
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: JavascriptRuntimeTimeoutException) {
            ReadiumCfiJavascriptResult.Failure(EpubCfiFailure.JAVASCRIPT_RUNTIME_TIMEOUT)
        } catch (_: JavascriptResultTooLargeException) {
            ReadiumCfiJavascriptResult.Failure(EpubCfiFailure.JAVASCRIPT_RESULT_TOO_LARGE)
        } catch (_: Exception) {
            ReadiumCfiJavascriptResult.Success(null)
        }
        return when (evaluation) {
            is ReadiumCfiJavascriptResult.Failure -> evaluation
            is ReadiumCfiJavascriptResult.Success -> evaluation.value.toInvocationResult()
        }
    }

    private fun JSONObject?.toInvocationResult(): ReadiumCfiJavascriptResult<Any?> = when {
        this == null -> ReadiumCfiJavascriptResult.Failure(
            EpubCfiFailure.JAVASCRIPT_RUNTIME_UNAVAILABLE
        )

        !optBoolean(P.FIELD_OK) -> {
            val errorCode = optJSONObject(P.FIELD_ERROR)?.optString(P.FIELD_CODE).orEmpty()
            ReadiumCfiJavascriptResult.Failure(errorCode.toCfiFailure())
        }

        else -> ReadiumCfiJavascriptResult.Success(
            opt(P.FIELD_VALUE).takeUnless { it === JSONObject.NULL }
        )
    }

    private fun boundedInvocationScript(
        method: CfiRuntimeMethod,
        serializedArguments: String
    ): String =
        """
        (() => {
          const envelope = JSON.stringify(
            window.${P.GLOBAL}.${method.wireName}($serializedArguments)
          );
          return envelope.length <= $MAX_RUNTIME_ENVELOPE_LENGTH
            ? envelope
            : JSON.stringify({ ${P.FIELD_OK}: false, ${P.FIELD_ERROR}: { ${P.FIELD_CODE}: "${P.ERROR_RESULT_TOO_LARGE}" } });
        })();
        """.trimIndent()

    private suspend fun ensureInstalledWithinDeadline(navigator: EpubNavigatorFragment): Boolean {
        if (readInstalledRuntimeVersion(navigator) == P.RUNTIME_VERSION) return true
        val installed = evaluateJavascriptWithin(
            navigator = navigator,
            script = "$installationScript\nwindow.${P.GLOBAL}.${P.METHOD_RUNTIME_VERSION}();",
            timeoutMillis = JAVASCRIPT_INSTALL_TIMEOUT_MILLIS
        )
        return installed.decodeJavascriptString() == P.RUNTIME_VERSION
    }

    private suspend fun evaluateEnvelope(
        navigator: EpubNavigatorFragment,
        script: String
    ): JSONObject? = try {
        val evaluated = evaluateJavascriptWithin(
            navigator,
            script,
            JAVASCRIPT_CALL_TIMEOUT_MILLIS
        ) ?: return null
        if (evaluated.length > MAX_EVALUATED_RESULT_LENGTH) {
            throw JavascriptResultTooLargeException()
        }
        val json = JSONTokener(evaluated).nextValue() as? String
            ?: error("CFI runtime did not return JSON.")
        if (json.length > MAX_RUNTIME_ENVELOPE_LENGTH) {
            throw JavascriptResultTooLargeException()
        }
        JSONObject(json)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (tooLarge: JavascriptResultTooLargeException) {
        throw tooLarge
    } catch (_: Exception) {
        null
    }
}

internal sealed interface JavascriptArgument {
    fun source(): String

    data class StringValue(private val value: String) : JavascriptArgument {
        override fun source(): String = JSONObject.quote(value)
    }

    data class NumberValue(private val value: Int) : JavascriptArgument {
        override fun source(): String = value.toString()
    }

    data object NullValue : JavascriptArgument {
        override fun source(): String = "null"
    }
}

private suspend fun readInstalledRuntimeVersion(navigator: EpubNavigatorFragment): String? =
    evaluateJavascriptWithin(
        navigator = navigator,
        script = "window.${P.GLOBAL} && " +
            "window.${P.GLOBAL}.${P.METHOD_RUNTIME_VERSION}();",
        timeoutMillis = JAVASCRIPT_CALL_TIMEOUT_MILLIS
    ).decodeJavascriptString()

private suspend fun evaluateJavascriptWithin(
    navigator: EpubNavigatorFragment,
    script: String,
    timeoutMillis: Long
): String? {
    val result = withTimeoutOrNull(timeoutMillis) {
        JavascriptEvaluation(navigator.evaluateJavascript(script))
    } ?: throw JavascriptRuntimeTimeoutException()
    return result.value?.also { value ->
        if (value.length > MAX_EVALUATED_RESULT_LENGTH) {
            throw JavascriptResultTooLargeException()
        }
    }
}

private data class JavascriptEvaluation(val value: String?)

private class JavascriptRuntimeTimeoutException : RuntimeException()

private class JavascriptResultTooLargeException : RuntimeException()

private val CFI_FAILURES_BY_CODE = mapOf(
    P.ERROR_INVALID_CFI to EpubCfiFailure.INVALID_CFI,
    P.ERROR_UNSUPPORTED_CFI_FEATURE to EpubCfiFailure.UNSUPPORTED_CFI_FEATURE,
    P.ERROR_INVALID_PACKAGE_DOCUMENT to EpubCfiFailure.PACKAGE_DOCUMENT_MISSING,
    P.ERROR_PACKAGE_TARGET_NOT_FOUND to EpubCfiFailure.PACKAGE_TARGET_NOT_FOUND,
    P.ERROR_PACKAGE_TARGET_MISMATCH to EpubCfiFailure.PACKAGE_TARGET_NOT_FOUND,
    P.ERROR_UNSUPPORTED_FIXED_LAYOUT to EpubCfiFailure.UNSUPPORTED_FIXED_LAYOUT,
    P.ERROR_UNSUPPORTED_SCROLL_MODE to EpubCfiFailure.UNSUPPORTED_SCROLL_MODE,
    P.ERROR_UNSUPPORTED_WRITING_MODE to EpubCfiFailure.UNSUPPORTED_WRITING_MODE,
    P.ERROR_RESULT_TOO_LARGE to EpubCfiFailure.JAVASCRIPT_RESULT_TOO_LARGE,
    P.ERROR_DOM_TARGET_NOT_FOUND to EpubCfiFailure.DOM_TARGET_NOT_FOUND,
    P.ERROR_INVALID_RANGE to EpubCfiFailure.INVALID_RANGE,
    P.ERROR_SELECTION_UNAVAILABLE to EpubCfiFailure.SELECTION_UNAVAILABLE,
    P.ERROR_VISIBLE_POSITION_UNAVAILABLE to EpubCfiFailure.VISIBLE_POSITION_UNAVAILABLE,
    P.ERROR_MOVEMENT_ANCHOR_UNAVAILABLE to EpubCfiFailure.MOVEMENT_ANCHOR_UNAVAILABLE,
    P.ERROR_CFI_RUNTIME_FAILURE to EpubCfiFailure.CFI_RUNTIME_FAILURE
)

private fun String.toCfiFailure(): EpubCfiFailure =
    CFI_FAILURES_BY_CODE[this] ?: EpubCfiFailure.CFI_RUNTIME_FAILURE

private fun String?.decodeJavascriptString(): String? = this
    ?.takeUnless { it == "null" || it == "undefined" }
    ?.removeSurrounding("\"")
