package com.secondpasslibrary.reader.reader.annotations

import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiNavigator
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome

internal enum class ReaderAnnotationNavigationResult {
    NAVIGATED,
    INVALID_CFI,
    UNAVAILABLE
}

internal suspend fun navigateToReaderAnnotation(
    annotation: ReaderAnnotation,
    navigator: EpubCfiNavigator
): ReaderAnnotationNavigationResult {
    val cfi = runCatching { EpubCfi(annotation.cfi) }.getOrNull()
        ?: return ReaderAnnotationNavigationResult.INVALID_CFI
    val navigationAvailable =
        navigator.awaitNavigationAvailable() is EpubCfiOutcome.Success
    return if (!navigationAvailable) {
        ReaderAnnotationNavigationResult.UNAVAILABLE
    } else {
        when (navigator.goTo(cfi)) {
            is EpubCfiOutcome.Success -> ReaderAnnotationNavigationResult.NAVIGATED
            is EpubCfiOutcome.Failure -> ReaderAnnotationNavigationResult.UNAVAILABLE
        }
    }
}
