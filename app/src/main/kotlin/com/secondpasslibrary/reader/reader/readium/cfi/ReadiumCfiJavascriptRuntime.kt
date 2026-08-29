package com.secondpasslibrary.reader.reader.readium.cfi

import android.content.Context
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiFailure
import com.secondpasslibrary.reader.reader.cfi.EpubPackageDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.readium.r2.navigator.epub.EpubNavigatorFragment

private const val RUNTIME_VERSION = "1.12.7"
private const val CONTEXT_LENGTH = 64
private const val SELECTION_CONTEXT_LENGTH = 2_000
private const val MOVEMENT_QUOTE_LENGTH = 128
private const val MAX_SELECTED_TEXT_LENGTH = 64 * 1024
private const val MAX_RUNTIME_ENVELOPE_LENGTH = 256 * 1024
private const val MAX_EVALUATED_RESULT_LENGTH = 512 * 1024
private const val JAVASCRIPT_CALL_TIMEOUT_MILLIS = 10_000L
private const val JAVASCRIPT_INSTALL_TIMEOUT_MILLIS = 20_000L
private const val COLIBRIO_ASSET = "reader/cfi/colibrio-epubcfi-1.1.0.min.js"
private const val RUNTIME_ASSET = "reader/cfi/secondpass-epub-cfi-runtime.js"

internal class ReadiumCfiJavascriptRuntime(context: Context) {
    private val bridge = ReadiumCfiJavascriptBridge(context.applicationContext)

    suspend fun installationFailure(navigator: EpubNavigatorFragment): EpubCfiFailure? =
        bridge.installationFailure(navigator)

    suspend fun documentReady(
        navigator: EpubNavigatorFragment
    ): ReadiumCfiJavascriptResult<Boolean> = bridge.invoke(
        navigator = navigator,
        method = "isDocumentReady",
        arguments = emptyList()
    ).mapValue { it as? Boolean ?: error("CFI runtime readiness result is invalid.") }

    suspend fun resolvePackage(
        navigator: EpubNavigatorFragment,
        cfi: EpubCfi,
        packageDocument: EpubPackageDocument
    ): ReadiumCfiJavascriptResult<ReadiumPackageTarget> = bridge.invoke(
        navigator = navigator,
        method = "resolvePackage",
        arguments = listOf(
            JavascriptArgument.StringValue(cfi.value),
            JavascriptArgument.StringValue(packageDocument.packageXml),
            JavascriptArgument.StringValue(packageDocument.packagePath)
        )
    ).mapValue { value ->
        readPackageTarget(value)
    }

    suspend fun generatePackage(
        navigator: EpubNavigatorFragment,
        packageDocument: EpubPackageDocument,
        spineIndex: Int,
        idref: String,
        itemrefId: String?
    ): ReadiumCfiJavascriptResult<EpubCfi> = bridge.invoke(
        navigator = navigator,
        method = "generatePackage",
        arguments = listOf(
            JavascriptArgument.StringValue(packageDocument.packageXml),
            JavascriptArgument.StringValue(packageDocument.packagePath),
            JavascriptArgument.NumberValue(spineIndex),
            JavascriptArgument.StringValue(idref),
            itemrefId?.let(JavascriptArgument::StringValue) ?: JavascriptArgument.NullValue
        )
    ).mapValue { value -> EpubCfi(value as String) }

    suspend fun composeFullCfi(
        navigator: EpubNavigatorFragment,
        packageCfi: EpubCfi,
        contentCfi: EpubCfi
    ): ReadiumCfiJavascriptResult<EpubCfi> = bridge.invoke(
        navigator = navigator,
        method = "composeFullCfi",
        arguments = listOf(
            JavascriptArgument.StringValue(packageCfi.value),
            JavascriptArgument.StringValue(contentCfi.value)
        )
    ).mapValue { value -> EpubCfi(value as String) }

    suspend fun resolveContent(
        navigator: EpubNavigatorFragment,
        cfi: EpubCfi,
        packageDocument: EpubPackageDocument,
        packageTarget: ReadiumEpubPackageTarget
    ): ReadiumCfiJavascriptResult<ReadiumContentResolution> = bridge.invoke(
        navigator = navigator,
        method = "resolveContent",
        arguments = listOf(
            JavascriptArgument.StringValue(cfi.value),
            JavascriptArgument.StringValue(packageDocument.packageXml),
            JavascriptArgument.StringValue(packageDocument.packagePath),
            JavascriptArgument.NumberValue(packageTarget.spineIndex),
            JavascriptArgument.StringValue(packageTarget.idref),
            packageTarget.itemrefId?.let(JavascriptArgument::StringValue)
                ?: JavascriptArgument.NullValue,
            JavascriptArgument.StringValue(packageTarget.resourceHref)
        )
    ).mapValue(::readContentResolution)

    suspend fun verifyContentTarget(
        navigator: EpubNavigatorFragment,
        cfi: EpubCfi,
        packageTarget: ReadiumEpubPackageTarget,
        resolution: ReadiumContentResolution
    ): ReadiumCfiJavascriptResult<ReadiumContentTargetVerification> = bridge.invoke(
        navigator = navigator,
        method = "verifyContentTarget",
        arguments = listOf(
            JavascriptArgument.StringValue(cfi.value),
            JavascriptArgument.StringValue(packageTarget.resourceHref),
            JavascriptArgument.StringValue(resolution.kind),
            resolution.selectedText?.let(JavascriptArgument::StringValue)
                ?: JavascriptArgument.NullValue,
            resolution.prefix?.let(JavascriptArgument::StringValue)
                ?: JavascriptArgument.NullValue,
            resolution.suffix?.let(JavascriptArgument::StringValue)
                ?: JavascriptArgument.NullValue,
            JavascriptArgument.StringValue(resolution.movementAnchor.exact),
            resolution.movementAnchor.before?.let(JavascriptArgument::StringValue)
                ?: JavascriptArgument.NullValue,
            resolution.movementAnchor.after?.let(JavascriptArgument::StringValue)
                ?: JavascriptArgument.NullValue
        )
    ).mapValue { value ->
        val verification = value as? JSONObject
            ?: error("CFI runtime verification result is invalid.")
        ReadiumContentTargetVerification(
            semanticMatch = verification.getBoolean("semanticMatch"),
            visible = verification.getBoolean("visible")
        )
    }

    suspend fun generateSelection(
        navigator: EpubNavigatorFragment
    ): ReadiumCfiJavascriptResult<ReadiumContentSelection?> = bridge.invoke(
        navigator = navigator,
        method = "generateSelectionContentCfi",
        arguments = emptyList()
    ).mapValue { value ->
        val selection = value as? JSONObject ?: return@mapValue null
        ReadiumContentSelection(
            contentCfi = EpubCfi(selection.getString("contentCfi")),
            selectedText = selection.getString("selectedText").also {
                require(it.length <= MAX_SELECTED_TEXT_LENGTH)
            },
            prefix = selection.boundedSelectionContext("prefix"),
            suffix = selection.boundedSelectionContext("suffix")
        )
    }

    suspend fun generateVisiblePosition(
        navigator: EpubNavigatorFragment
    ): ReadiumCfiJavascriptResult<EpubCfi> = bridge.invoke(
        navigator = navigator,
        method = "generateVisiblePositionContentCfi",
        arguments = emptyList()
    ).mapValue { value -> EpubCfi(value as String) }

    suspend fun visiblePointTargets(
        navigator: EpubNavigatorFragment,
        candidates: Map<String, EpubCfi>,
        packageDocument: EpubPackageDocument,
        spineIndex: Int,
        idref: String,
        itemrefId: String?,
        resourceHref: String
    ): ReadiumCfiJavascriptResult<Set<String>> = bridge.invoke(
        navigator = navigator,
        method = "visiblePointTargets",
        arguments = listOf(
            JavascriptArgument.StringValue(
                JSONArray().apply {
                    candidates.forEach { (id, cfi) ->
                        put(JSONObject().put("id", id).put("cfi", cfi.value))
                    }
                }.toString()
            ),
            JavascriptArgument.StringValue(packageDocument.packageXml),
            JavascriptArgument.StringValue(packageDocument.packagePath),
            JavascriptArgument.NumberValue(spineIndex),
            JavascriptArgument.StringValue(idref),
            itemrefId?.let(JavascriptArgument::StringValue) ?: JavascriptArgument.NullValue,
            JavascriptArgument.StringValue(resourceHref)
        )
    ).mapValue { value ->
        val results = value as? JSONArray
            ?: error("CFI runtime point-visibility result is invalid.")
        buildSet {
            repeat(results.length()) { index ->
                val result = results.getJSONObject(index)
                if (result.getBoolean("visible")) add(result.getString("id"))
            }
        }
    }
}

private class ReadiumCfiJavascriptBridge(private val context: Context) {
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
        method: String,
        arguments: List<JavascriptArgument>
    ): ReadiumCfiJavascriptResult<Any?> {
        val serializedArguments = arguments.joinToString(
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
            ReadiumCfiJavascriptResult.Failure(
                EpubCfiFailure.JAVASCRIPT_RUNTIME_TIMEOUT
            )
        } catch (_: JavascriptResultTooLargeException) {
            ReadiumCfiJavascriptResult.Failure(
                EpubCfiFailure.JAVASCRIPT_RESULT_TOO_LARGE
            )
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

        !optBoolean("ok") -> {
            val errorCode = optJSONObject("error")?.optString("code").orEmpty()
            ReadiumCfiJavascriptResult.Failure(errorCode.toCfiFailure())
        }

        else -> ReadiumCfiJavascriptResult.Success(
            opt("value").takeUnless { it === JSONObject.NULL }
        )
    }

    private fun boundedInvocationScript(method: String, serializedArguments: String): String =
        """
        (() => {
          const envelope = JSON.stringify(
            window.__secondPassEpubCfi.$method($serializedArguments)
          );
          return envelope.length <= $MAX_RUNTIME_ENVELOPE_LENGTH
            ? envelope
            : JSON.stringify({ ok: false, error: { code: "RESULT_TOO_LARGE" } });
        })();
        """.trimIndent()

    private suspend fun ensureInstalledWithinDeadline(navigator: EpubNavigatorFragment): Boolean {
        if (readInstalledRuntimeVersion(navigator) == RUNTIME_VERSION) return true
        val installed = evaluateJavascriptWithin(
            navigator = navigator,
            script = "$installationScript\nwindow.__secondPassEpubCfi.runtimeVersion();",
            timeoutMillis = JAVASCRIPT_INSTALL_TIMEOUT_MILLIS
        )
        return installed.decodeJavascriptString() == RUNTIME_VERSION
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

private suspend fun readInstalledRuntimeVersion(navigator: EpubNavigatorFragment): String? =
    evaluateJavascriptWithin(
        navigator = navigator,
        script = "window.__secondPassEpubCfi && " +
            "window.__secondPassEpubCfi.runtimeVersion();",
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

internal data class ReadiumPackageTarget(
    val spineIndex: Int,
    val itemrefId: String?,
    val idref: String,
    val kind: String
)

internal fun readPackageTarget(value: Any?): ReadiumPackageTarget {
    val target = value as? JSONObject ?: error("CFI runtime package result is invalid.")
    return ReadiumPackageTarget(
        spineIndex = target.getInt("spineIndex"),
        itemrefId = target.nullableString("itemrefId"),
        idref = target.getString("idref"),
        kind = target.getString("kind")
    )
}

internal data class ReadiumContentSelection(
    val contentCfi: EpubCfi,
    val selectedText: String,
    val prefix: String?,
    val suffix: String?
)

internal data class ReadiumContentResolution(
    val kind: String,
    val selectedText: String?,
    val prefix: String?,
    val suffix: String?,
    val movementAnchor: ReadiumTextQuoteAnchor
)

internal data class ReadiumTextQuoteAnchor(
    val exact: String,
    val before: String?,
    val after: String?
)

internal data class ReadiumContentTargetVerification(
    val semanticMatch: Boolean,
    val visible: Boolean
)

internal sealed interface ReadiumCfiJavascriptResult<out T> {
    data class Success<T>(val value: T) : ReadiumCfiJavascriptResult<T>

    data class Failure(val reason: EpubCfiFailure) : ReadiumCfiJavascriptResult<Nothing>
}

private sealed interface JavascriptArgument {
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

private inline fun <T, R> ReadiumCfiJavascriptResult<T>.mapValue(
    transform: (T) -> R
): ReadiumCfiJavascriptResult<R> = when (this) {
    is ReadiumCfiJavascriptResult.Failure -> this

    is ReadiumCfiJavascriptResult.Success -> runCatching { transform(value) }.fold(
        onSuccess = ReadiumCfiJavascriptResult<R>::Success,
        onFailure = {
            ReadiumCfiJavascriptResult.Failure(EpubCfiFailure.CFI_RUNTIME_FAILURE)
        }
    )
}

private val CFI_FAILURES_BY_CODE = mapOf(
    "INVALID_CFI" to EpubCfiFailure.INVALID_CFI,
    "UNSUPPORTED_CFI_FEATURE" to EpubCfiFailure.UNSUPPORTED_CFI_FEATURE,
    "INVALID_PACKAGE_DOCUMENT" to EpubCfiFailure.PACKAGE_DOCUMENT_MISSING,
    "PACKAGE_TARGET_NOT_FOUND" to EpubCfiFailure.PACKAGE_TARGET_NOT_FOUND,
    "PACKAGE_TARGET_MISMATCH" to EpubCfiFailure.PACKAGE_TARGET_NOT_FOUND,
    "UNSUPPORTED_FIXED_LAYOUT" to EpubCfiFailure.UNSUPPORTED_FIXED_LAYOUT,
    "UNSUPPORTED_SCROLL_MODE" to EpubCfiFailure.UNSUPPORTED_SCROLL_MODE,
    "UNSUPPORTED_WRITING_MODE" to EpubCfiFailure.UNSUPPORTED_WRITING_MODE,
    "RESULT_TOO_LARGE" to EpubCfiFailure.JAVASCRIPT_RESULT_TOO_LARGE,
    "DOM_TARGET_NOT_FOUND" to EpubCfiFailure.DOM_TARGET_NOT_FOUND,
    "INVALID_RANGE" to EpubCfiFailure.INVALID_RANGE,
    "SELECTION_UNAVAILABLE" to EpubCfiFailure.SELECTION_UNAVAILABLE,
    "VISIBLE_POSITION_UNAVAILABLE" to EpubCfiFailure.VISIBLE_POSITION_UNAVAILABLE,
    "MOVEMENT_ANCHOR_UNAVAILABLE" to EpubCfiFailure.MOVEMENT_ANCHOR_UNAVAILABLE,
    "CFI_RUNTIME_FAILURE" to EpubCfiFailure.CFI_RUNTIME_FAILURE
)

private fun String.toCfiFailure(): EpubCfiFailure =
    CFI_FAILURES_BY_CODE[this] ?: EpubCfiFailure.CFI_RUNTIME_FAILURE

private fun String?.decodeJavascriptString(): String? = this
    ?.takeUnless { it == "null" || it == "undefined" }
    ?.removeSurrounding("\"")

private fun JSONObject.nullableString(name: String): String? =
    takeUnless { isNull(name) }?.optString(name)?.takeIf(String::isNotEmpty)

private fun readContentResolution(value: Any?): ReadiumContentResolution {
    val resolution = value as? JSONObject ?: error("CFI runtime content result is invalid.")
    val movementAnchor = resolution.getJSONObject("movementAnchor")
    val kind = resolution.getString("kind").also {
        require(it == "point" || it == "range")
    }
    val selectedText = resolution.nullableString("selectedText")?.also {
        require(it.length <= MAX_SELECTED_TEXT_LENGTH)
    }
    require(
        (kind == "point" && selectedText == null) ||
            (kind == "range" && selectedText != null)
    )
    return ReadiumContentResolution(
        kind = kind,
        selectedText = selectedText,
        prefix = resolution.boundedContext("prefix"),
        suffix = resolution.boundedContext("suffix"),
        movementAnchor = ReadiumTextQuoteAnchor(
            exact = movementAnchor.getString("exact").also {
                require(it.isNotBlank() && it.length <= MOVEMENT_QUOTE_LENGTH)
            },
            before = movementAnchor.boundedContext("before"),
            after = movementAnchor.boundedContext("after")
        )
    )
}

private fun JSONObject.boundedContext(name: String): String? = nullableString(name)?.also {
    require(it.length <= CONTEXT_LENGTH)
}

private fun JSONObject.boundedSelectionContext(name: String): String? = nullableString(name)?.also {
    require(it.length <= SELECTION_CONTEXT_LENGTH)
}
