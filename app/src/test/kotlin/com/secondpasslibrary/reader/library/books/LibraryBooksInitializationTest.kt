package com.secondpasslibrary.reader.library.books

import com.secondpasslibrary.client.BookOrdering
import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.client.LibrarySearchOrdering
import com.secondpasslibrary.reader.library.DEFAULT_LIBRARY_PAGE_SIZE
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryBooksInitializationTest {
    @Test
    fun `normal Books initial load uses page one and preserves server order`() = runTest {
        val client = FakeLibraryClient().apply {
            listCall = { page(1, listOf("second", "first"), total = 8, hasNext = true) }
        }
        val controller = libraryBooksController(client)

        controller.initializeBrowse(profile())
        advanceUntilIdle()

        val state = controller.state.value
        assertEquals(LibraryBooksMode.BROWSE, state.mode)
        assertEquals(LibraryBooksOrdering.Browse(BookOrdering.TITLE), state.ordering)
        assertEquals(DEFAULT_LIBRARY_PAGE_SIZE, state.pageSize)
        assertEquals(listOf("second", "first"), state.books.map { it.id })
        assertEquals(8, state.totalCount)
        assertTrue(state.hasNext)
        assertEquals(1, state.currentPage)
        assertFalse(state.initialLoading)
        assertEquals(listOf(1), client.bookRequests.map { it.page })
        assertTrue(client.searchRequests.isEmpty())
    }

    @Test
    fun `broad search route initializes explicit broad mode`() = runTest {
        val client = FakeLibraryClient().apply {
            searchCall = { page(1, listOf("broad-result"), total = 1) }
        }
        val controller = libraryBooksController(client)

        controller.initializeBroadSearch(profile(), "octavia butler")
        advanceUntilIdle()

        val state = controller.state.value
        assertEquals(LibraryBooksMode.BROAD_SEARCH, state.mode)
        assertEquals("octavia butler", state.committedQuery)
        assertEquals(
            LibraryBooksOrdering.BroadSearch(LibrarySearchOrdering.TITLE),
            state.ordering
        )
        assertEquals(listOf("broad-result"), state.books.map { it.id })
        assertEquals("octavia butler", client.searchRequests.single().q)
        assertTrue(client.bookRequests.isEmpty())
    }

    @Test
    fun `entry query remains independent from connection identity`() = runTest {
        val client = FakeLibraryClient()
        val controller = libraryBooksController(client)
        val profile = profile()
        controller.initialize(profile, LibraryBooksMode.BROWSE, "first", LibraryScope.Global)
        advanceUntilIdle()

        controller.initialize(profile, LibraryBooksMode.BROWSE, "second", LibraryScope.Global)
        advanceUntilIdle()

        assertEquals(listOf("first", "second"), client.bookRequests.map { it.q })
    }

    @Test
    fun `re-pairing resets Books state for an unchanged entry`() = runTest {
        val client = FakeLibraryClient()
        val controller = libraryBooksController(client)
        val profile = profile()
        controller.initializeBrowse(profile)
        advanceUntilIdle()

        controller.initializeBrowse(profile.copy(clientSessionId = "replacement-session"))
        advanceUntilIdle()

        assertEquals(2, client.bookRequests.size)
    }

    @Test
    fun `ordering change resets accumulated paging to page one`() = runTest {
        val client = FakeLibraryClient().apply {
            listCall = { request ->
                if (request.ordering == BookOrdering.AUTHOR) {
                    page(1, listOf("author-ordered"), 1)
                } else if (request.page == 1) {
                    page(1, listOf("first"), 2, hasNext = true)
                } else {
                    page(2, listOf("second"), 2)
                }
            }
        }
        val controller = libraryBooksController(client)
        controller.initializeBrowse(profile())
        advanceUntilIdle()
        controller.loadNextPage()
        advanceUntilIdle()

        controller.changeBrowseOrdering(BookOrdering.AUTHOR)
        advanceUntilIdle()

        assertEquals(listOf("author-ordered"), controller.state.value.books.map { it.id })
        assertEquals(1, controller.state.value.currentPage)
        assertEquals(
            LibraryBooksOrdering.Browse(BookOrdering.AUTHOR),
            controller.state.value.ordering
        )
        assertEquals(BookOrdering.AUTHOR, client.bookRequests.last().ordering)
    }

    @Test
    fun `committed normal search resets paging and stays on Books endpoint`() = runTest {
        val client = FakeLibraryClient().apply {
            listCall = { request ->
                if (request.q == "dune") {
                    page(1, listOf("searched"), 1)
                } else {
                    page(1, listOf("browse"), 2, hasNext = true)
                }
            }
        }
        val controller = libraryBooksController(client)
        controller.initializeBrowse(profile())
        advanceUntilIdle()

        controller.commitBrowseQuery("dune")
        assertEquals(listOf("browse"), controller.state.value.books.map { it.id })
        assertTrue(controller.state.value.initialLoading)
        advanceUntilIdle()

        assertEquals(LibraryBooksMode.BROWSE, controller.state.value.mode)
        assertEquals("dune", controller.state.value.committedQuery)
        assertEquals(listOf("searched"), controller.state.value.books.map { it.id })
        assertEquals(1, controller.state.value.currentPage)
        assertEquals("dune", client.bookRequests.last().q)
        assertTrue(client.searchRequests.isEmpty())
    }
}
