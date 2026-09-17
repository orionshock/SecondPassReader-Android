package com.secondpasslibrary.reader.library.chrome

import com.secondpasslibrary.reader.library.LibraryResultState
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
                result = LibraryResultState.AuthorIndex()
            ).searchPlaceholder()
        )
        assertEquals(
            "Search series",
            LibraryState(
                result = LibraryResultState.SeriesIndex()
            ).searchPlaceholder()
        )
    }

    @Test
    fun `selected Author keeps its scoped Book search wording`() {
        val state =
            LibraryState(
                result =
                    LibraryResultState.AuthorBooks(
                        com.secondpasslibrary.reader.library.axis.PagedLibraryAxisDetailState(
                            "author-1"
                        ),
                        books =
                            LibraryBooksState(filter = LibraryBooksFilter.Author("author-1"))
                    )
            )

        assertEquals("Search books by this author", state.searchPlaceholder())
    }
}
