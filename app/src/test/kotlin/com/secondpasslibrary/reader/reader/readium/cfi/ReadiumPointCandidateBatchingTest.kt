package com.secondpasslibrary.reader.reader.readium.cfi

import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubLayout
import com.secondpasslibrary.reader.reader.cfi.EpubSpineItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadiumPointCandidateBatchingTest {
    @Test
    fun `only exact point targets in active spine reach geometry batches`() {
        val candidates = linkedMapOf(
            "active" to EpubCfi("epubcfi(/6/2!/4/2:0)"),
            "other" to EpubCfi("epubcfi(/6/4!/4/2:0)"),
            "range" to EpubCfi("epubcfi(/6/2!,/4/2:0,/4/2:1)"),
            "unresolved" to EpubCfi("epubcfi(/6/6!/4/2:0)")
        )
        val targets = mapOf(
            "active" to packageTarget(spineIndex = 0, idref = "one", kind = "point"),
            "other" to packageTarget(spineIndex = 1, idref = "two", kind = "point"),
            "range" to packageTarget(spineIndex = 0, idref = "one", kind = "range")
        )

        assertEquals(
            mapOf("active" to candidates.getValue("active")),
            activePointCandidates(candidates, targets, spineItem())
        )
    }

    @Test
    fun `candidate batches are bounded without dropping order or identity`() {
        val candidates = (0 until 2_001).associate { index ->
            "bookmark-$index" to EpubCfi("epubcfi(/6/2!/4/2:$index)")
        }

        val chunks = pointCandidateChunks(candidates)

        assertEquals(listOf(1_000, 1_000, 1), chunks.map { it.size })
        assertEquals(
            candidates,
            chunks.flatMap { it.entries }.associate { it.key to it.value }
        )
    }

    private fun packageTarget(spineIndex: Int, idref: String, kind: String) = ReadiumPackageTarget(
        spineIndex = spineIndex,
        itemrefId = "spine-$idref",
        idref = idref,
        kind = kind
    )

    private fun spineItem() = EpubSpineItem(
        index = 0,
        id = "spine-one",
        idref = "one",
        resourceHref = "text/one.xhtml",
        mediaType = "application/xhtml+xml",
        layout = EpubLayout.REFLOWABLE
    )
}
