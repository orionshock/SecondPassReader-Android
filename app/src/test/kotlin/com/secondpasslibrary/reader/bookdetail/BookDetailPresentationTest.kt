package com.secondpasslibrary.reader.bookdetail

import com.secondpasslibrary.client.PublicationDatePrecision
import com.secondpasslibrary.reader.library.libraryBookDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookDetailPresentationTest {
    @Test
    fun `description strips markup hidden blocks and normalizes whitespace`() {
        val source = """
            <h2>First &amp; foremost</h2>
            <p>A   paragraph<br>with a line.</p>
            <ul><li>One</li><li>Two &#8212; yes</li></ul>
            <script>secret()</script>
        """.trimIndent()

        assertEquals(
            "First & foremost\nA paragraph\nwith a line.\n\u2022 One\n\u2022 Two \u2014 yes",
            BookDescriptionPresenter.present(source)
        )
        assertNull(BookDescriptionPresenter.present("<p> &nbsp; </p>"))
    }

    @Test
    fun `publication and exact Series index remain presentation values`() {
        val detail = libraryBookDetail("book").copy(
            publishedYear = 2026,
            publishedMonth = 8,
            publishedDay = 17,
            publicationDatePrecision = PublicationDatePrecision.DAY,
            series = com.secondpasslibrary.client.BookSeriesSummary(
                "series",
                "Cases",
                "Cases",
                com.secondpasslibrary.client.SeriesIndex.fromExactValue("1.20")
            )
        )

        val presentation = detail.toPresentation()

        assertEquals("Cases \u00b7 1.20", presentation.seriesLabel)
        assertEquals("Aug 17, 2026", presentation.publicationLabel)
    }

    @Test
    fun `file sizes use bounded human readable binary units`() {
        assertEquals("0 B", fileSizeLabel(0))
        assertEquals("1 KiB", fileSizeLabel(1024))
        assertEquals("1.5 MiB", fileSizeLabel(1572864))
    }
}
