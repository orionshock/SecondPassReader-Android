package com.secondpasslibrary.reader.reader.annotations

import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderAnnotationDecorationControllerTest {
    @Test
    fun `projection preserves canonical CFI and annotation kind`() {
        val highlight = highlight("highlight-1", "epubcfi(/6/4!/4/2,/1:0,/1:4)")
        val bookmark = bookmark("bookmark-1", "epubcfi(/6/4!/4/2:0)")

        assertEquals(
            ReaderAnnotationDecoration(
                annotationId = "highlight-1",
                cfi = EpubCfi("epubcfi(/6/4!/4/2,/1:0,/1:4)"),
                kind = ReaderAnnotationKind.HIGHLIGHT,
                color = ReaderAnnotationColor.BLUE
            ),
            highlight.toDecoration()
        )
        assertEquals(ReaderAnnotationKind.BOOKMARK, bookmark.toDecoration()?.kind)
        assertEquals(bookmark.cfi, bookmark.toDecoration()?.cfi?.value)
    }

    @Test
    fun `invalid canonical CFI is omitted conservatively`() {
        assertNull(highlight("highlight-1", "").toDecoration())
    }

    @Test
    fun `annotation refresh replaces the authoritative decoration set`() = runTest {
        val target = RecordingDecorations()
        val controller = ReaderAnnotationDecorationController()

        controller.replace("session-1", target, listOf(highlight("one"), bookmark("bookmark")))
        controller.replace("session-1", target, listOf(highlight("two")))

        assertEquals(listOf("two"), target.replacements.last().map { it.annotationId })
        assertEquals(0, target.clearCount)
    }

    @Test
    fun `Session replacement clears the old engine before installing the new set`() = runTest {
        val first = RecordingDecorations()
        val second = RecordingDecorations()
        val controller = ReaderAnnotationDecorationController()

        controller.replace("session-1", first, listOf(highlight("old")))
        controller.replace("session-2", second, listOf(highlight("new")))

        assertEquals(1, first.clearCount)
        assertEquals(listOf("new"), second.replacements.single().map { it.annotationId })
    }

    private class RecordingDecorations : ReaderAnnotationDecorations {
        override val failures = MutableStateFlow(
            emptyMap<String, ReaderAnnotationDecorationFailure>()
        )
        val replacements = mutableListOf<List<ReaderAnnotationDecoration>>()
        var clearCount = 0

        override suspend fun replace(decorations: List<ReaderAnnotationDecoration>) {
            replacements += decorations
        }

        override suspend fun clear() {
            clearCount += 1
        }
    }
}

private fun highlight(id: String): ReaderAnnotation.Highlight =
    highlight(id, "epubcfi(/6/4!/4/2,/1:0,/1:4)")

private fun highlight(id: String, cfi: String): ReaderAnnotation.Highlight =
    ReaderAnnotation.Highlight(
        id = id,
        clientId = "client-$id",
        cfi = cfi,
        locationLabel = "Chapter 1",
        updatedAt = "2026-08-25T00:00:00Z",
        quote = "Text",
        prefix = null,
        suffix = null,
        note = null,
        color = ReaderAnnotationColor.BLUE
    )

private fun bookmark(id: String): ReaderAnnotation.Bookmark = bookmark(id, "epubcfi(/6/4!/4/2:0)")

private fun bookmark(id: String, cfi: String): ReaderAnnotation.Bookmark =
    ReaderAnnotation.Bookmark(
        id = id,
        clientId = "client-$id",
        cfi = cfi,
        locationLabel = "Chapter 1",
        updatedAt = "2026-08-25T00:00:00Z"
    )
