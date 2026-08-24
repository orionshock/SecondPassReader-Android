package com.secondpasslibrary.reader.reader.readium.cfi

import android.content.Context
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiFailure
import com.secondpasslibrary.reader.reader.cfi.EpubPackageDocument
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import org.json.JSONTokener
import org.readium.r2.navigator.epub.EpubNavigatorFragment

private const val RUNTIME_VERSION = "1.8.0"
private const val CONTEXT_LENGTH = 64
private const val MOVEMENT_QUOTE_LENGTH = 128
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
        if (readInstalledRuntimeVersion(navigator) == RUNTIME_VERSION) return true
        val installed = navigator.evaluateJavascript(
            "$installationScript\nwindow.__secondPassEpubCfi.runtimeVersion();"
        )
        return installed.decodeJavascriptString() == RUNTIME_VERSION
    }

    suspend fun resolvePackage(
        navigator: EpubNavigatorFragment,
        cfi: EpubCfi,
        packageDocument: EpubPackageDocument
    ): ReadiumCfiJavascriptResult<ReadiumPackageTarget> = invoke(
        navigator = navigator,
        method = "resolvePackage",
        arguments = listOf(
            JavascriptArgument.StringValue(cfi.value),
            JavascriptArgument.StringValue(packageDocument.packageXml),
            JavascriptArgument.StringValue(packageDocument.packagePath)
        )
    ).mapValue { value ->
        val target = value as? JSONObject ?: error("CFI runtime package result is invalid.")
        ReadiumPackageTarget(
            spineIndex = target.getInt("spineIndex"),
            itemrefId = target.getString("itemrefId").ifBlank { null },
            idref = target.getString("idref"),
            kind = target.getString("kind")
        )
    }

    suspend fun generatePackage(
        navigator: EpubNavigatorFragment,
        packageDocument: EpubPackageDocument,
        spineIndex: Int,
        idref: String,
        itemrefId: String?
    ): ReadiumCfiJavascriptResult<EpubCfi> = invoke(
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
    ): ReadiumCfiJavascriptResult<EpubCfi> = invoke(
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
    ): ReadiumCfiJavascriptResult<ReadiumContentResolution> = invoke(
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
        packageDocument: EpubPackageDocument,
        packageTarget: ReadiumEpubPackageTarget,
        resolution: ReadiumContentResolution
    ): ReadiumCfiJavascriptResult<ReadiumContentTargetVerification> = invoke(
        navigator = navigator,
        method = "verifyContentTarget",
        arguments = listOf(
            JavascriptArgument.StringValue(cfi.value),
            JavascriptArgument.StringValue(packageDocument.packageXml),
            JavascriptArgument.StringValue(packageDocument.packagePath),
            JavascriptArgument.NumberValue(packageTarget.spineIndex),
            JavascriptArgument.StringValue(packageTarget.idref),
            packageTarget.itemrefId?.let(JavascriptArgument::StringValue)
                ?: JavascriptArgument.NullValue,
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
    ): ReadiumCfiJavascriptResult<ReadiumContentSelection?> = invoke(
        navigator = navigator,
        method = "generateSelectionContentCfi",
        arguments = emptyList()
    ).mapValue { value ->
        val selection = value as? JSONObject ?: return@mapValue null
        ReadiumContentSelection(
            contentCfi = EpubCfi(selection.getString("contentCfi")),
            selectedText = selection.getString("selectedText"),
            prefix = selection.nullableString("prefix"),
            suffix = selection.nullableString("suffix")
        )
    }

    suspend fun generateVisiblePosition(
        navigator: EpubNavigatorFragment
    ): ReadiumCfiJavascriptResult<EpubCfi> = invoke(
        navigator = navigator,
        method = "generateVisiblePositionContentCfi",
        arguments = emptyList()
    ).mapValue { value -> EpubCfi(value as String) }

    private suspend fun invoke(
        navigator: EpubNavigatorFragment,
        method: String,
        arguments: List<JavascriptArgument>
    ): ReadiumCfiJavascriptResult<Any?> {
        val serializedArguments = arguments.joinToString(
            ",",
            transform = JavascriptArgument::source
        )
        val script = "JSON.stringify(window.__secondPassEpubCfi.$method($serializedArguments));"
        val envelope = if (hasRuntime(navigator)) {
            evaluateEnvelope(navigator, script)
        } else {
            null
        }
        return when {
            envelope == null -> ReadiumCfiJavascriptResult.Failure(
                EpubCfiFailure.JAVASCRIPT_RUNTIME_UNAVAILABLE
            )

            !envelope.optBoolean("ok") -> {
                val errorCode = envelope.optJSONObject("error")?.optString("code").orEmpty()
                ReadiumCfiJavascriptResult.Failure(errorCode.toCfiFailure())
            }

            else -> ReadiumCfiJavascriptResult.Success(
                envelope.opt("value").takeUnless { it === JSONObject.NULL }
            )
        }
    }

    private suspend fun hasRuntime(navigator: EpubNavigatorFragment): Boolean = try {
        ensureInstalled(navigator)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }

    private suspend fun evaluateEnvelope(
        navigator: EpubNavigatorFragment,
        script: String
    ): JSONObject? = try {
        val evaluated = navigator.evaluateJavascript(script)
        val json = JSONTokener(evaluated).nextValue() as? String
            ?: error("CFI runtime did not return JSON.")
        JSONObject(json)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}

private suspend fun readInstalledRuntimeVersion(navigator: EpubNavigatorFragment): String? =
    navigator.evaluateJavascript(
        "window.__secondPassEpubCfi && " +
            "window.__secondPassEpubCfi.runtimeVersion();"
    ).decodeJavascriptString()

internal data class ReadiumPackageTarget(
    val spineIndex: Int,
    val itemrefId: String?,
    val idref: String,
    val kind: String
)

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
            ReadiumCfiJavascriptResult.Failure(EpubCfiFailure.JAVASCRIPT_RUNTIME_UNAVAILABLE)
        }
    )
}

private fun String.toCfiFailure(): EpubCfiFailure = when (this) {
    "INVALID_CFI" -> EpubCfiFailure.INVALID_CFI

    "UNSUPPORTED_CFI_FEATURE" -> EpubCfiFailure.UNSUPPORTED_CFI_FEATURE

    "INVALID_PACKAGE_DOCUMENT" -> EpubCfiFailure.PACKAGE_DOCUMENT_MISSING

    "PACKAGE_TARGET_NOT_FOUND", "PACKAGE_TARGET_MISMATCH" ->
        EpubCfiFailure.PACKAGE_TARGET_NOT_FOUND

    "UNSUPPORTED_FIXED_LAYOUT" -> EpubCfiFailure.UNSUPPORTED_FIXED_LAYOUT

    "UNSUPPORTED_SCROLL_MODE" -> EpubCfiFailure.UNSUPPORTED_SCROLL_MODE

    "UNSUPPORTED_WRITING_MODE" -> EpubCfiFailure.UNSUPPORTED_WRITING_MODE

    "DOM_TARGET_NOT_FOUND" -> EpubCfiFailure.DOM_TARGET_NOT_FOUND

    "INVALID_RANGE" -> EpubCfiFailure.INVALID_RANGE

    "SELECTION_UNAVAILABLE" -> EpubCfiFailure.SELECTION_UNAVAILABLE

    "VISIBLE_POSITION_UNAVAILABLE" -> EpubCfiFailure.VISIBLE_POSITION_UNAVAILABLE

    else -> EpubCfiFailure.JAVASCRIPT_RUNTIME_UNAVAILABLE
}

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
    val selectedText = resolution.nullableString("selectedText")
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
