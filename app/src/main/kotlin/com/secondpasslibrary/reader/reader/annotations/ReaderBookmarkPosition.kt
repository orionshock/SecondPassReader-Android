package com.secondpasslibrary.reader.reader.annotations

import com.secondpasslibrary.reader.reader.cfi.EpubCfiNavigator
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import com.secondpasslibrary.reader.reader.cfi.EpubCfiPosition

/** Captures the same durable point-position seam used by Reader progress. */
internal suspend fun captureReaderBookmarkPosition(
    navigator: EpubCfiNavigator
): EpubCfiOutcome<EpubCfiPosition> = when (val available = navigator.awaitNavigationAvailable()) {
    is EpubCfiOutcome.Failure -> available
    is EpubCfiOutcome.Success -> navigator.currentPositionWithContext()
}
