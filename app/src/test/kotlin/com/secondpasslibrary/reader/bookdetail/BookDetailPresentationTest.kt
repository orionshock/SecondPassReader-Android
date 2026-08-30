package com.secondpasslibrary.reader.bookdetail

import androidx.compose.ui.unit.dp
import com.secondpasslibrary.client.PublicationDatePrecision
import com.secondpasslibrary.reader.design.components.AppBarNavigation
import com.secondpasslibrary.reader.library.axis.libraryBookDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookDetailPresentationTest {
    @Test
    fun `Book Detail app bar uses origin and authoritative Book title`() {
        val state =
            BookDetailState(
                detail = libraryBookDetail("book").copy(title = "Academ's Fury")
            )

        val presentation = state.appBarPresentation("Library")

        assertEquals(AppBarNavigation.BACK, presentation.navigation)
        assertEquals("Library", presentation.context)
        assertEquals("Academ's Fury", presentation.title)
    }

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
        assertEquals("series", presentation.seriesNavigationId)
        assertEquals("Aug 17, 2026", presentation.publicationLabel)
    }

    @Test
    fun `only an unambiguous Author is navigable`() {
        val single = libraryBookDetail("book").copy(
            authors = listOf(com.secondpasslibrary.client.BookAuthorSummary("author", "Author"))
        ).toPresentation()
        val multiple = libraryBookDetail("book").copy(
            authors = listOf(
                com.secondpasslibrary.client.BookAuthorSummary("one", "One"),
                com.secondpasslibrary.client.BookAuthorSummary("two", "Two")
            )
        ).toPresentation()

        assertEquals("author", single.authorNavigationId)
        assertNull(multiple.authorNavigationId)
        assertEquals("One, Two", multiple.authorsLabel)
    }

    @Test
    fun `missing optional metadata remains absent`() {
        val presentation = libraryBookDetail("book").toPresentation()

        assertNull(presentation.subtitle)
        assertNull(presentation.seriesLabel)
        assertNull(presentation.authorsLabel)
        assertNull(presentation.publicationLabel)
        assertNull(presentation.fileLabel)
    }

    @Test
    fun `Book Detail width selects wide medium and narrow compositions`() {
        assertEquals(BookDetailLayout.NARROW, bookDetailLayoutForWidth(599.dp))
        assertEquals(BookDetailLayout.MEDIUM, bookDetailLayoutForWidth(600.dp))
        assertEquals(BookDetailLayout.WIDE, bookDetailLayoutForWidth(900.dp))
    }

    @Test
    fun `Read book is visible but unavailable while existing actions remain enabled`() {
        val actions = bookDetailActionPresentations().associateBy { it.kind }

        assertFalse(requireNotNull(actions[BookDetailActionKind.READ_BOOK]).enabled)
        assertEquals("Read book", actions[BookDetailActionKind.READ_BOOK]?.label)
        assertTrue(requireNotNull(actions[BookDetailActionKind.READING_SESSIONS]).enabled)
        assertTrue(requireNotNull(actions[BookDetailActionKind.ADD_TO_SHELF]).enabled)
    }

    @Test
    fun `Read book becomes enabled only when the Reader entry is available`() {
        val actions = bookDetailActionPresentations(readBookEnabled = true).associateBy { it.kind }

        assertTrue(requireNotNull(actions[BookDetailActionKind.READ_BOOK]).enabled)
    }

    @Test
    fun `offline Book Detail disables server actions without disabling local read`() {
        val actions = bookDetailActionPresentations(
            readBookEnabled = true,
            serverActionsAvailable = false
        ).associateBy { it.kind }

        assertTrue(requireNotNull(actions[BookDetailActionKind.READ_BOOK]).enabled)
        assertFalse(requireNotNull(actions[BookDetailActionKind.READING_SESSIONS]).enabled)
        assertFalse(requireNotNull(actions[BookDetailActionKind.ADD_TO_SHELF]).enabled)
    }

    @Test
    fun `file sizes use bounded human readable binary units`() {
        assertEquals("0 B", fileSizeLabel(0))
        assertEquals("1 KiB", fileSizeLabel(1024))
        assertEquals("1.5 MiB", fileSizeLabel(1572864))
    }
}
