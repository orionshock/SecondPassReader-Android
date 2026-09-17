package com.secondpasslibrary.reader.reader.readium.cfi

import android.content.Context
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiFailure
import com.secondpasslibrary.reader.reader.cfi.EpubPackageDocument
import com.secondpasslibrary.reader.reader.readium.cfi.CfiProtocol as P
import org.json.JSONArray
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFragment

internal class ReadiumCfiJavascriptRuntime(context: Context) {
    private val bridge = ReadiumCfiJavascriptBridge(context.applicationContext)

    suspend fun installationFailure(navigator: EpubNavigatorFragment): EpubCfiFailure? =
        bridge.installationFailure(navigator)

    suspend fun documentReady(
        navigator: EpubNavigatorFragment
    ): ReadiumCfiJavascriptResult<Boolean> = bridge.invoke(
        navigator = navigator,
        method = CfiRuntimeMethod.IS_DOCUMENT_READY,
        arguments = emptyMap()
    ).mapValue { it as? Boolean ?: error("CFI runtime readiness result is invalid.") }

    suspend fun resolvePackage(
        navigator: EpubNavigatorFragment,
        cfi: EpubCfi,
        packageDocument: EpubPackageDocument
    ): ReadiumCfiJavascriptResult<ReadiumPackageTarget> = bridge.invoke(
        navigator = navigator,
        method = CfiRuntimeMethod.RESOLVE_PACKAGE,
        arguments = mapOf(
            P.ARG_CFI to JavascriptArgument.StringValue(cfi.value),
            P.ARG_PACKAGE_XML to JavascriptArgument.StringValue(packageDocument.packageXml),
            P.ARG_PACKAGE_PATH to JavascriptArgument.StringValue(packageDocument.packagePath)
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
        method = CfiRuntimeMethod.RESOLVE_PACKAGE_CANDIDATES,
        arguments = mapOf(
            P.ARG_SERIALIZED_CANDIDATES to JavascriptArgument.StringValue(candidates.serialized()),
            P.ARG_PACKAGE_XML to JavascriptArgument.StringValue(packageDocument.packageXml),
            P.ARG_PACKAGE_PATH to JavascriptArgument.StringValue(packageDocument.packagePath)
        )
    ).mapValue { value ->
        val results = value as? JSONArray
            ?: error("CFI runtime package-candidate result is invalid.")
        buildMap {
            repeat(results.length()) { index ->
                val result = results.getJSONObject(index)
                put(result.getString(P.FIELD_ID), readPackageTarget(result))
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
        method = CfiRuntimeMethod.GENERATE_PACKAGE,
        arguments = mapOf(
            P.ARG_PACKAGE_XML to JavascriptArgument.StringValue(packageDocument.packageXml),
            P.ARG_PACKAGE_PATH to JavascriptArgument.StringValue(packageDocument.packagePath),
            P.ARG_SPINE_INDEX to JavascriptArgument.NumberValue(spineIndex),
            P.ARG_IDREF to JavascriptArgument.StringValue(idref),
            P.ARG_ITEMREF_ID to (
                itemrefId?.let(JavascriptArgument::StringValue)
                    ?: JavascriptArgument.NullValue
                )
        )
    ).mapValue { value -> EpubCfi(value as String) }

    suspend fun composeFullCfi(
        navigator: EpubNavigatorFragment,
        packageCfi: EpubCfi,
        contentCfi: EpubCfi
    ): ReadiumCfiJavascriptResult<EpubCfi> = bridge.invoke(
        navigator = navigator,
        method = CfiRuntimeMethod.COMPOSE_FULL_CFI,
        arguments = mapOf(
            P.ARG_PACKAGE_CFI to JavascriptArgument.StringValue(packageCfi.value),
            P.ARG_CONTENT_CFI to JavascriptArgument.StringValue(contentCfi.value)
        )
    ).mapValue { value -> EpubCfi(value as String) }

    suspend fun resolveContent(
        navigator: EpubNavigatorFragment,
        cfi: EpubCfi,
        packageDocument: EpubPackageDocument,
        packageTarget: ReadiumEpubPackageTarget
    ): ReadiumCfiJavascriptResult<ReadiumContentResolution> = bridge.invoke(
        navigator = navigator,
        method = CfiRuntimeMethod.RESOLVE_CONTENT,
        arguments = mapOf(
            P.ARG_CFI to JavascriptArgument.StringValue(cfi.value),
            P.ARG_PACKAGE_XML to JavascriptArgument.StringValue(packageDocument.packageXml),
            P.ARG_PACKAGE_PATH to JavascriptArgument.StringValue(packageDocument.packagePath),
            P.ARG_SPINE_INDEX to JavascriptArgument.NumberValue(packageTarget.spineIndex),
            P.ARG_IDREF to JavascriptArgument.StringValue(packageTarget.idref),
            P.ARG_ITEMREF_ID to (
                packageTarget.itemrefId?.let(JavascriptArgument::StringValue)
                    ?: JavascriptArgument.NullValue
                ),
            P.ARG_RESOURCE_HREF to JavascriptArgument.StringValue(packageTarget.resourceHref)
        )
    ).mapValue(::readContentResolution)

    suspend fun verifyContentTarget(
        navigator: EpubNavigatorFragment,
        cfi: EpubCfi,
        packageTarget: ReadiumEpubPackageTarget,
        resolution: ReadiumContentResolution
    ): ReadiumCfiJavascriptResult<ReadiumContentTargetVerification> = bridge.invoke(
        navigator = navigator,
        method = CfiRuntimeMethod.VERIFY_CONTENT_TARGET,
        arguments = mapOf(
            P.ARG_CFI to JavascriptArgument.StringValue(cfi.value),
            P.ARG_RESOURCE_HREF to JavascriptArgument.StringValue(packageTarget.resourceHref),
            P.ARG_KIND to JavascriptArgument.StringValue(resolution.kind),
            P.ARG_SELECTED_TEXT to (
                resolution.selectedText?.let(JavascriptArgument::StringValue)
                    ?: JavascriptArgument.NullValue
                ),
            P.ARG_PREFIX to (
                resolution.prefix?.let(JavascriptArgument::StringValue)
                    ?: JavascriptArgument.NullValue
                ),
            P.ARG_SUFFIX to (
                resolution.suffix?.let(JavascriptArgument::StringValue)
                    ?: JavascriptArgument.NullValue
                ),
            P.ARG_EXACT to JavascriptArgument.StringValue(resolution.movementAnchor.exact),
            P.ARG_BEFORE to (
                resolution.movementAnchor.before?.let(JavascriptArgument::StringValue)
                    ?: JavascriptArgument.NullValue
                ),
            P.ARG_AFTER to (
                resolution.movementAnchor.after?.let(JavascriptArgument::StringValue)
                    ?: JavascriptArgument.NullValue
                )
        )
    ).mapValue { value ->
        val verification = value as? JSONObject
            ?: error("CFI runtime verification result is invalid.")
        ReadiumContentTargetVerification(
            semanticMatch = verification.getBoolean(P.FIELD_SEMANTIC_MATCH),
            visible = verification.getBoolean(P.FIELD_VISIBLE)
        )
    }

    suspend fun generateSelection(
        navigator: EpubNavigatorFragment
    ): ReadiumCfiJavascriptResult<ReadiumContentSelection?> = bridge.invoke(
        navigator = navigator,
        method = CfiRuntimeMethod.GENERATE_SELECTION_CONTENT_CFI,
        arguments = emptyMap()
    ).mapValue { value ->
        val selection = value as? JSONObject ?: return@mapValue null
        ReadiumContentSelection(
            contentCfi = EpubCfi(selection.getString(P.FIELD_CONTENT_CFI)),
            selectedText = selection.getString(P.FIELD_SELECTED_TEXT).also {
                require(it.length <= P.MAX_SELECTED_TEXT_LENGTH)
            },
            prefix = selection.boundedSelectionContext(P.FIELD_PREFIX),
            suffix = selection.boundedSelectionContext(P.FIELD_SUFFIX)
        )
    }

    suspend fun generateVisiblePosition(
        navigator: EpubNavigatorFragment
    ): ReadiumCfiJavascriptResult<EpubCfi> = bridge.invoke(
        navigator = navigator,
        method = CfiRuntimeMethod.GENERATE_VISIBLE_POSITION_CONTENT_CFI,
        arguments = emptyMap()
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
        method = CfiRuntimeMethod.VISIBLE_POINT_TARGETS,
        arguments = mapOf(
            P.ARG_SERIALIZED_CANDIDATES to JavascriptArgument.StringValue(
                candidates.serialized()
            ),
            P.ARG_PACKAGE_XML to JavascriptArgument.StringValue(packageDocument.packageXml),
            P.ARG_PACKAGE_PATH to JavascriptArgument.StringValue(packageDocument.packagePath),
            P.ARG_SPINE_INDEX to JavascriptArgument.NumberValue(spineIndex),
            P.ARG_IDREF to JavascriptArgument.StringValue(idref),
            P.ARG_ITEMREF_ID to (
                itemrefId?.let(JavascriptArgument::StringValue)
                    ?: JavascriptArgument.NullValue
                ),
            P.ARG_RESOURCE_HREF to JavascriptArgument.StringValue(resourceHref)
        )
    ).mapValue { value ->
        val results = value as? JSONArray
            ?: error("CFI runtime point-visibility result is invalid.")
        buildSet {
            repeat(results.length()) { index ->
                val result = results.getJSONObject(index)
                if (result.getBoolean(P.FIELD_VISIBLE)) add(result.getString(P.FIELD_ID))
            }
        }
    }
}

private fun Map<String, EpubCfi>.serialized(): String = JSONArray().apply {
    forEach { (id, cfi) ->
        put(JSONObject().put(P.FIELD_ID, id).put(P.FIELD_CFI, cfi.value))
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
        spineIndex = target.getInt(P.FIELD_SPINE_INDEX),
        itemrefId = target.nullableString(P.FIELD_ITEMREF_ID),
        idref = target.getString(P.FIELD_IDREF),
        kind = target.getString(P.FIELD_KIND)
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
    val movementAnchor = resolution.getJSONObject(P.FIELD_MOVEMENT_ANCHOR)
    val kind = resolution.getString(P.FIELD_KIND).also {
        require(it == P.KIND_POINT || it == P.KIND_RANGE)
    }
    val selectedText = resolution.nullableString(P.FIELD_SELECTED_TEXT)?.also {
        require(it.length <= P.MAX_SELECTED_TEXT_LENGTH)
    }
    require(
        (kind == P.KIND_POINT && selectedText == null) ||
            (kind == P.KIND_RANGE && selectedText != null)
    )
    return ReadiumContentResolution(
        kind = kind,
        selectedText = selectedText,
        prefix = resolution.boundedSelectionContext(P.FIELD_PREFIX),
        suffix = resolution.boundedSelectionContext(P.FIELD_SUFFIX),
        movementAnchor = ReadiumTextQuoteAnchor(
            exact = movementAnchor.getString(P.FIELD_EXACT).also {
                require(it.isNotBlank() && it.length <= P.MOVEMENT_QUOTE_LENGTH)
            },
            before = movementAnchor.boundedContext(P.FIELD_BEFORE),
            after = movementAnchor.boundedContext(P.FIELD_AFTER)
        )
    )
}

private fun JSONObject.boundedContext(name: String): String? = nullableString(name)?.also {
    require(it.length <= P.CONTEXT_LENGTH)
}

private fun JSONObject.boundedSelectionContext(name: String): String? = nullableString(name)?.also {
    require(it.length <= P.SELECTION_CONTEXT_LENGTH)
}
