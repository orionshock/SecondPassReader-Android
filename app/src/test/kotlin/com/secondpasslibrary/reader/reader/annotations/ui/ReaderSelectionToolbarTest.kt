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
    fun `toolbar prefers below selection and clamps horizontally`() {
        val offset = selectionToolbarOffset(
            selection(bounds = EpubSelectionBounds(0f, 200f, 20f, 230f)),
            toolbarSize = Size(300f, 60f),
            viewportWidth = 500,
            viewportHeight = 400,
            edgePadding = 8f,
            selectionSpacing = 8f,
            fallbackTop = 64f
        )

        assertEquals(IntOffset(8, 238), offset)
    }

    @Test
    fun `toolbar uses available side space near the bottom`() {
        val offset = selectionToolbarOffset(
            selection(bounds = EpubSelectionBounds(420f, 250f, 490f, 280f)),
            toolbarSize = Size(240f, 80f),
            viewportWidth = 500,
            viewportHeight = 320,
            edgePadding = 8f,
            selectionSpacing = 8f,
            fallbackTop = 64f
        )

        assertEquals(IntOffset(172, 225), offset)
    }

    @Test
    fun `toolbar stays below selections in either column`() {
        val leftColumn = selectionToolbarOffset(
            selection(bounds = EpubSelectionBounds(120f, 140f, 220f, 170f)),
            toolbarSize = Size(180f, 48f),
            viewportWidth = 800,
            viewportHeight = 400,
            edgePadding = 8f,
            selectionSpacing = 8f,
            fallbackTop = 64f
        )
        val rightColumn = selectionToolbarOffset(
            selection(bounds = EpubSelectionBounds(580f, 140f, 680f, 170f)),
            toolbarSize = Size(180f, 48f),
            viewportWidth = 800,
            viewportHeight = 400,
            edgePadding = 8f,
            selectionSpacing = 8f,
            fallbackTop = 64f
        )

        assertEquals(IntOffset(80, 178), leftColumn)
        assertEquals(IntOffset(540, 178), rightColumn)
    }

    @Test
    fun `toolbar falls above when no lower or side space exists`() {
        val offset = selectionToolbarOffset(
            selection(bounds = EpubSelectionBounds(0f, 300f, 600f, 330f)),
            toolbarSize = Size(200f, 64f),
            viewportWidth = 600,
            viewportHeight = 400,
            edgePadding = 8f,
            selectionSpacing = 8f,
            fallbackTop = 64f
        )

        assertEquals(IntOffset(200, 228), offset)
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
