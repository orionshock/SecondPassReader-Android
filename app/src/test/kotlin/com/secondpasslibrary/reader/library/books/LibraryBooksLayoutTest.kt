package com.secondpasslibrary.reader.library.books

import androidx.compose.ui.unit.dp
import com.secondpasslibrary.client.BookOrdering
import com.secondpasslibrary.reader.design.book.CompactBookRowLayout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryBooksLayoutTest {
    @Test
    fun `Library list uses wide rows only at the tablet breakpoint`() {
        assertEquals(CompactBookRowLayout.COMPACT, libraryBookRowLayoutForWidth(899.dp))
        assertEquals(CompactBookRowLayout.WIDE, libraryBookRowLayoutForWidth(900.dp))
    }

    @Test
    fun `display preference loads and changes independently of server query state`() = runTest {
        val preference = FakeDisplayPreferenceStore(LibraryBooksLayout.LIST)
        val controller =
            LibraryBooksController(
                FakeClientProvider(FakeLibraryClient()),
                preference,
                this
            )

        controller.initializeBrowse(profile())
        advanceUntilIdle()
        assertEquals(LibraryBooksLayout.LIST, controller.state.value.layout)

        controller.setLayout(LibraryBooksLayout.GRID)
        advanceUntilIdle()
        assertEquals(LibraryBooksLayout.GRID, controller.state.value.layout)
        assertEquals(LibraryBooksLayout.GRID, preference.layout)
        assertEquals(
            BookOrdering.TITLE,
            (controller.state.value.ordering as LibraryBooksOrdering.Browse).value
        )
    }
}
