package com.secondpasslibrary.reader.reader.annotations.selection

import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiSelection
import kotlin.math.roundToInt

internal data class ReaderSelection(
    val cfi: EpubCfi,
    val selectedText: String,
    val prefix: String?,
    val suffix: String?,
    val locationLabel: String
)

internal fun EpubCfiSelection.toReaderSelection(): ReaderSelection? {
    if (selectedText.isBlank()) return null
    return ReaderSelection(
        cfi = cfi,
        selectedText = selectedText,
        prefix = prefix,
        suffix = suffix,
        locationLabel = readerLocationLabel(chapterOrdinal, totalProgression)
    )
}

internal fun readerLocationLabel(chapterOrdinal: Int, totalProgression: Double?): String {
    require(chapterOrdinal > 0) { "Chapter ordinal must be positive." }
    val chapter = chapterOrdinal.toString().padStart(2, '0')
    val percentage = totalProgression
        ?.coerceIn(0.0, 1.0)
        ?.times(100)
        ?.roundToInt()
    return if (percentage == null) "Chapter $chapter" else "Chapter $chapter · $percentage%"
}
