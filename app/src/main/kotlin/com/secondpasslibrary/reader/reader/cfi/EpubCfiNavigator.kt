package com.secondpasslibrary.reader.reader.cfi

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

internal interface EpubCfiNavigator {
    val readiness: StateFlow<EpubCfiReadiness>

    /** Waits for a live renderer viewport without making renderer attachment route identity. */
    suspend fun awaitNavigationAvailable(): EpubCfiOutcome<Unit> = when (
        val state = readiness.first {
            it == EpubCfiReadiness.Available ||
                it is EpubCfiReadiness.Failed ||
                it == EpubCfiReadiness.Closed
        }
    ) {
        EpubCfiReadiness.Available -> EpubCfiOutcome.Success(Unit)

        is EpubCfiReadiness.Failed -> EpubCfiOutcome.Failure(state.reason)

        EpubCfiReadiness.Closed,
        EpubCfiReadiness.PreparingDocument,
        EpubCfiReadiness.AwaitingViewport ->
            EpubCfiOutcome.Failure(EpubCfiFailure.NAVIGATOR_UNAVAILABLE)
    }

    suspend fun goTo(cfi: EpubCfi): EpubCfiOutcome<Unit>

    suspend fun currentPosition(): EpubCfiOutcome<EpubCfi> =
        when (val captured = currentPositionWithContext()) {
            is EpubCfiOutcome.Failure -> captured
            is EpubCfiOutcome.Success -> EpubCfiOutcome.Success(captured.value.cfi)
        }

    suspend fun currentPositionWithContext(): EpubCfiOutcome<EpubCfiPosition> =
        EpubCfiOutcome.Failure(EpubCfiFailure.VISIBLE_POSITION_UNAVAILABLE)

    suspend fun currentSelection(): EpubCfiOutcome<EpubCfiSelection?>

    suspend fun resolve(cfi: EpubCfi): EpubCfiOutcome<EpubCfiResolution>
}

/** Renderer-neutral availability of the live publication viewport used for CFI operations. */
internal sealed interface EpubCfiReadiness {
    data object AwaitingViewport : EpubCfiReadiness

    data object PreparingDocument : EpubCfiReadiness

    data object Available : EpubCfiReadiness

    data class Failed(val reason: EpubCfiFailure) : EpubCfiReadiness

    data object Closed : EpubCfiReadiness
}

internal data class EpubCfiPosition(
    val cfi: EpubCfi,
    val chapterOrdinal: Int,
    val totalProgression: Double?
)

internal sealed interface EpubCfiOutcome<out T> {
    data class Success<T>(val value: T) : EpubCfiOutcome<T>

    data class Failure(val reason: EpubCfiFailure) : EpubCfiOutcome<Nothing>
}

internal data class EpubCfiSelection(
    val cfi: EpubCfi,
    val selectedText: String,
    val prefix: String?,
    val suffix: String?,
    val chapterOrdinal: Int,
    val totalProgression: Double?
)

/**
 * Renderer-neutral evidence for a durable CFI target. [originalCfi] remains canonical; the text
 * context is an adapter input for later decoration anchoring, not a replacement location.
 */
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
    JAVASCRIPT_RUNTIME_TIMEOUT,
    JAVASCRIPT_RESULT_TOO_LARGE,
    DOM_TARGET_NOT_FOUND,
    INVALID_RANGE,
    SELECTION_UNAVAILABLE,
    VISIBLE_POSITION_UNAVAILABLE,
    MOVEMENT_ANCHOR_UNAVAILABLE,
    RESOURCE_CHANGED_DURING_OPERATION,
    NAVIGATION_TIMEOUT,
    NAVIGATION_FAILED,
    CFI_RUNTIME_FAILURE
}
