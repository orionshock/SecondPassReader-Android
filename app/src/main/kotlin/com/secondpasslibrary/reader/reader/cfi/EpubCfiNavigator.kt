package com.secondpasslibrary.reader.reader.cfi

internal interface EpubCfiNavigator {
    suspend fun goTo(cfi: EpubCfi): EpubCfiOutcome<Unit>

    suspend fun currentPosition(): EpubCfiOutcome<EpubCfi>

    suspend fun currentSelection(): EpubCfiOutcome<EpubCfiSelection?>

    suspend fun resolve(cfi: EpubCfi): EpubCfiOutcome<EpubCfiResolution>
}

internal sealed interface EpubCfiOutcome<out T> {
    data class Success<T>(val value: T) : EpubCfiOutcome<T>

    data class Failure(val reason: EpubCfiFailure) : EpubCfiOutcome<Nothing>
}

internal data class EpubCfiSelection(
    val cfi: EpubCfi,
    val selectedText: String,
    val prefix: String?,
    val suffix: String?
)

internal data class EpubCfiResolution(
    val originalCfi: EpubCfi,
    val resourceHref: String,
    val kind: EpubCfiTargetKind,
    val selectedText: String?,
    val prefix: String?,
    val suffix: String?
)

internal enum class EpubCfiTargetKind { POINT, RANGE }

internal enum class EpubCfiFailure {
    INVALID_CFI,
    UNSUPPORTED_CFI_FEATURE,
    UNSUPPORTED_FIXED_LAYOUT,
    UNSUPPORTED_SCROLL_MODE,
    UNSUPPORTED_WRITING_MODE,
    PACKAGE_DOCUMENT_MISSING,
    PACKAGE_TARGET_NOT_FOUND,
    RESOURCE_NOT_IN_READING_ORDER,
    NAVIGATOR_UNAVAILABLE,
    JAVASCRIPT_RUNTIME_UNAVAILABLE,
    DOM_TARGET_NOT_FOUND,
    INVALID_RANGE,
    SELECTION_UNAVAILABLE,
    VISIBLE_POSITION_UNAVAILABLE,
    NAVIGATION_FAILED
}
