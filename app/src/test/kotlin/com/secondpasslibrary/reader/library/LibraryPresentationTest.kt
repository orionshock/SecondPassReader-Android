package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.AuthorOrdering
import com.secondpasslibrary.client.LibraryGroupSummary
import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.client.SeriesOrdering
import com.secondpasslibrary.reader.design.components.AppBarContextEmphasis
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.library.axis.PagedLibraryAxisState
import com.secondpasslibrary.reader.library.books.LibraryBooksState
import com.secondpasslibrary.reader.library.chrome.LibraryGroupSelectorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryPresentationTest {
    @Test
    fun `global axes use Library context with singular and plural counts`() {
        val books =
            LibraryState(
                books = LibraryBooksState(totalCount = 1, currentPage = 1, initialLoading = false)
            ).appBarPresentation()
        val authors =
            LibraryState(
                axis = LibraryAxis.AUTHORS,
                resultKind = LibraryResultKind.AUTHOR_INDEX,
                authors =
                    PagedLibraryAxisState(
                        ordering = AuthorOrdering.NAME,
                        totalCount = 2,
                        currentPage = 1
                    )
            ).appBarPresentation()
        val series =
            LibraryState(
                axis = LibraryAxis.SERIES,
                resultKind = LibraryResultKind.SERIES_INDEX,
                series =
                    PagedLibraryAxisState(
                        ordering = SeriesOrdering.NAME,
                        totalCount = 1,
                        currentPage = 1
                    )
            ).appBarPresentation()

        assertEquals("Library", books.context)
        assertEquals("Books", books.title)
        assertEquals("1 book", books.metadata)
        assertEquals(AppBarContextEmphasis.TITLE, books.contextEmphasis)
        assertEquals("Authors", authors.title)
        assertEquals("2 authors", authors.metadata)
        assertEquals("Series", series.title)
        assertEquals("1 series", series.metadata)
        assertNull(books.contextIcon)
    }

    @Test
    fun `selected group contributes semantic context without replacing axis`() {
        val state =
            LibraryState(
                axis = LibraryAxis.AUTHORS,
                resultKind = LibraryResultKind.AUTHOR_INDEX,
                scope = LibraryScope.Group("group-1"),
                groupSelector =
                    LibraryGroupSelectorState(
                        loaded = true,
                        groups = listOf(LibraryGroupSummary("group-1", "Common Room", false))
                    ),
                authors = PagedLibraryAxisState(ordering = AuthorOrdering.NAME)
            )

        val presentation = state.appBarPresentation()

        assertEquals("Library:", presentation.context)
        assertEquals("Common Room", presentation.contextDetail)
        assertEquals(AppIcon.Group, presentation.contextIcon)
        assertEquals("Authors", presentation.title)
        assertEquals(" — ", presentation.separator)
    }
}
