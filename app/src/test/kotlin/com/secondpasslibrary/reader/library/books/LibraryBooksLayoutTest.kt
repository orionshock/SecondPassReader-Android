package com.secondpasslibrary.reader.library.books

import com.secondpasslibrary.client.BookOrdering
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryBooksLayoutTest {
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
