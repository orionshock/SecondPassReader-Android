package com.secondpasslibrary.reader.reader.readium.cfi

import android.content.Context
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiFailure
import com.secondpasslibrary.reader.reader.cfi.EpubPackageDocument
import org.json.JSONArray
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFragment

private const val CONTEXT_LENGTH = 64
private const val SELECTION_CONTEXT_LENGTH = 2_000
private const val MOVEMENT_QUOTE_LENGTH = 128
private const val MAX_SELECTED_TEXT_LENGTH = 64 * 1024

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

    suspend fun resolvePackageCandidates(
        navigator: EpubNavigatorFragment,
        candidates: Map<String, EpubCfi>,
        packageDocument: EpubPackageDocument
    ): ReadiumCfiJavascriptResult<Map<String, ReadiumPackageTarget>> = bridge.invoke(
        navigator = navigator,
        method = "resolvePackageCandidates",
        arguments = listOf(
            JavascriptArgument.StringValue(candidates.serialized()),
            JavascriptArgument.StringValue(packageDocument.packageXml),
            JavascriptArgument.StringValue(packageDocument.packagePath)
        )
    ).mapValue { value ->
        val results = value as? JSONArray
            ?: error("CFI runtime package-candidate result is invalid.")
        buildMap {
            repeat(results.length()) { index ->
                val result = results.getJSONObject(index)
                put(result.getString("id"), readPackageTarget(result))
            }
        }
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
                candidates.serialized()
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

private fun Map<String, EpubCfi>.serialized(): String = JSONArray().apply {
    forEach { (id, cfi) ->
        put(JSONObject().put("id", id).put("cfi", cfi.value))
    }
}.toString()

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
        prefix = resolution.boundedSelectionContext("prefix"),
        suffix = resolution.boundedSelectionContext("suffix"),
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
