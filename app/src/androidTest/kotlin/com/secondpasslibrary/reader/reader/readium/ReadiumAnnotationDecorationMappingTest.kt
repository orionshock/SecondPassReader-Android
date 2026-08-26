package com.secondpasslibrary.reader.reader.readium

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationDecoration
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderAnnotationKind
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiResolution
import com.secondpasslibrary.reader.reader.cfi.EpubCfiTargetKind
import com.secondpasslibrary.reader.reader.cfi.EpubLayout
import com.secondpasslibrary.reader.reader.readium.cfi.ReadiumEpubPackageTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.Decoration
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

@RunWith(AndroidJUnit4::class)
class ReadiumAnnotationDecorationMappingTest {
    @Test
    fun exactRangeCreatesOrdinaryReadiumHighlightWithStoredColor() {
        val cfi = EpubCfi("epubcfi(/6/4!/4/2,/1:0,/1:4)")
        val decoration = annotation(cfi).toReadiumDecoration(
            target = target(),
            resolution = EpubCfiResolution(
                originalCfi = cfi,
                resourceHref = "text/chapter.xhtml",
                kind = EpubCfiTargetKind.RANGE,
                selectedText = "Exact quote",
                prefix = "Before ",
                suffix = " after"
            )
        )

        requireNotNull(decoration)
        assertEquals("annotation-1", decoration.id)
        assertEquals("Exact quote", decoration.locator.text.highlight)
        assertEquals("Before ", decoration.locator.text.before)
        assertEquals(" after", decoration.locator.text.after)
        val style = decoration.style as Decoration.Style.Highlight
        assertEquals(0xFF3B82F6.toInt(), style.tint)
        assertTrue(!style.isActive)
    }

    @Test
    fun bookmarkAndPointTargetsAreNotApproximatedAsHighlights() {
        val cfi = EpubCfi("epubcfi(/6/4!/4/2:0)")
        val bookmark = annotation(cfi).copy(kind = ReaderAnnotationKind.BOOKMARK, color = null)
        val point = resolution(cfi, EpubCfiTargetKind.POINT)

        assertNull(bookmark.toReadiumDecoration(target(), point))
        assertNull(annotation(cfi).toReadiumDecoration(target(), point))
    }

    private fun annotation(cfi: EpubCfi) = ReaderAnnotationDecoration(
        annotationId = "annotation-1",
        cfi = cfi,
        kind = ReaderAnnotationKind.HIGHLIGHT,
        color = ReaderAnnotationColor.BLUE
    )

    private fun resolution(cfi: EpubCfi, kind: EpubCfiTargetKind) = EpubCfiResolution(
        originalCfi = cfi,
        resourceHref = "text/chapter.xhtml",
        kind = kind,
        selectedText = "Exact quote",
        prefix = null,
        suffix = null
    )

    private fun target(): ReadiumEpubPackageTarget {
        val url = requireNotNull(Url("text/chapter.xhtml"))
        val mediaType = requireNotNull(MediaType("application/xhtml+xml"))
        return ReadiumEpubPackageTarget(
            spineIndex = 0,
            itemrefId = "itemref-1",
            idref = "chapter-1",
            resourceHref = "text/chapter.xhtml",
            resourceUrl = url,
            mediaType = mediaType,
            resourceLink = Link(href = url, mediaType = mediaType),
            kind = EpubCfiTargetKind.RANGE,
            layout = EpubLayout.REFLOWABLE
        )
    }
}
