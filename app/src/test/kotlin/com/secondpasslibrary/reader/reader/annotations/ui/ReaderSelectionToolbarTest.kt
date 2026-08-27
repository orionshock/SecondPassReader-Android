package com.secondpasslibrary.reader.reader.annotations.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntOffset
import com.secondpasslibrary.reader.reader.annotations.selection.ReaderSelection
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubSelectionBounds
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSelectionToolbarTest {
    @Test
    fun `toolbar prefers above selection and clamps horizontally`() {
        val offset = selectionToolbarOffset(
            selection(bounds = EpubSelectionBounds(0f, 200f, 20f, 230f)),
            toolbarSize = Size(300f, 60f),
            viewportWidth = 500,
            viewportHeight = 400,
            edgePadding = 8f,
            selectionSpacing = 8f,
            fallbackTop = 64f
        )

        assertEquals(IntOffset(8, 132), offset)
    }

    @Test
    fun `toolbar falls below top selection and remains inside viewport`() {
        val offset = selectionToolbarOffset(
            selection(bounds = EpubSelectionBounds(420f, 12f, 490f, 42f)),
            toolbarSize = Size(240f, 80f),
            viewportWidth = 500,
            viewportHeight = 120,
            edgePadding = 8f,
            selectionSpacing = 8f,
            fallbackTop = 64f
        )

        assertEquals(IntOffset(252, 32), offset)
    }

    private fun selection(bounds: EpubSelectionBounds) = ReaderSelection(
        cfi = EpubCfi("epubcfi(/6/2!/4/2,/1:0,/1:4)"),
        selectedText = "Selected",
        prefix = null,
        suffix = null,
        locationLabel = "Chapter 01",
        bounds = bounds
    )
}
