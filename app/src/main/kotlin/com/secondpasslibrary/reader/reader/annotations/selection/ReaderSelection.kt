package com.secondpasslibrary.reader.reader.annotations.selection

import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiSelection
import com.secondpasslibrary.reader.reader.cfi.EpubSelectionBounds
import com.secondpasslibrary.reader.reader.location.ReaderSavedLocationLabelPolicy

internal data class ReaderSelection(
    val cfi: EpubCfi,
    val selectedText: String,
    val prefix: String?,
    val suffix: String?,
    val locationLabel: String,
    val bounds: EpubSelectionBounds? = null
)

internal fun EpubCfiSelection.toReaderSelection(): ReaderSelection? {
    if (selectedText.isBlank()) return null
    return ReaderSelection(
        cfi = cfi,
        selectedText = selectedText,
        prefix = prefix,
        suffix = suffix,
        locationLabel = ReaderSavedLocationLabelPolicy.create(
            totalProgression,
            sectionLabel,
            chapterOrdinal
        ),
        bounds = bounds
    )
}
