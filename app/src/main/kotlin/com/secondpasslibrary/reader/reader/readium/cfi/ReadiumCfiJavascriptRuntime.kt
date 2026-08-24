package com.secondpasslibrary.reader.reader.readium.cfi

import android.content.Context
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiFailure
import com.secondpasslibrary.reader.reader.cfi.EpubPackageDocument
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import org.json.JSONTokener
import org.readium.r2.navigator.epub.EpubNavigatorFragment

private const val RUNTIME_VERSION = "1.2.0"
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

    private suspend fun installedVersion(navigator: EpubNavigatorFragment): String? =
        navigator.evaluateJavascript(
            "window.__secondPassEpubCfi && " +
                "window.__secondPassEpubCfi.runtimeVersion();"
        ).decodeJavascriptString()

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

internal data class ReadiumPackageTarget(
    val spineIndex: Int,
    val itemrefId: String?,
    val idref: String,
    val kind: String
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

    "DOM_TARGET_NOT_FOUND" -> EpubCfiFailure.DOM_TARGET_NOT_FOUND

    "INVALID_RANGE" -> EpubCfiFailure.INVALID_RANGE

    "SELECTION_UNAVAILABLE" -> EpubCfiFailure.SELECTION_UNAVAILABLE

    else -> EpubCfiFailure.JAVASCRIPT_RUNTIME_UNAVAILABLE
}

private fun String?.decodeJavascriptString(): String? = this
    ?.takeUnless { it == "null" || it == "undefined" }
    ?.removeSurrounding("\"")
