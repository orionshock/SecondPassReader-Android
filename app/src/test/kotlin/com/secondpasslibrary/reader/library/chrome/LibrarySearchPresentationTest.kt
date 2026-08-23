package com.secondpasslibrary.reader.library.chrome

import com.secondpasslibrary.reader.library.LibraryAxis
import com.secondpasslibrary.reader.library.LibraryResultKind
import com.secondpasslibrary.reader.library.LibraryState
import com.secondpasslibrary.reader.library.books.LibraryBooksFilter
import com.secondpasslibrary.reader.library.books.LibraryBooksState
import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySearchPresentationTest {
    @Test
    fun `search placeholder remains contextual to axis`() {
        assertEquals("Search book titles", LibraryState().searchPlaceholder())
        assertEquals(
            "Search authors",
            LibraryState(
                axis = LibraryAxis.AUTHORS,
                resultKind = LibraryResultKind.AUTHOR_INDEX
            ).searchPlaceholder()
        )
        assertEquals(
            "Search series",
            LibraryState(
                axis = LibraryAxis.SERIES,
                resultKind = LibraryResultKind.SERIES_INDEX
            ).searchPlaceholder()
        )
    }

    @Test
    fun `selected Author keeps its scoped Book search wording`() {
        val state =
            LibraryState(
                axis = LibraryAxis.AUTHORS,
                resultKind = LibraryResultKind.BOOKS,
                books = LibraryBooksState(filter = LibraryBooksFilter.Author("author-1"))
            )

        assertEquals("Search books by this author", state.searchPlaceholder())
    }
}
