package com.secondpasslibrary.reader.library.books

import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.reader.library.LibraryAxis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryBooksContextTest {
    @Test
    fun `scope capability is hidden when disabled and defaults to All Library when enabled`() =
        runTest {
            val disabledClient = FakeLibraryClient()
            val disabled = libraryController(disabledClient)
            disabled.initialize(profile(), LibraryBooksEntry.Browse, advancedGroupsEnabled = false)
            advanceUntilIdle()
            assertFalse(disabled.state.value.advancedGroupsEnabled)
            assertTrue(disabledClient.groupRequests.isEmpty())

            val enabledClient = FakeLibraryClient().apply {
                groupCall = { options ->
                    if (options.page == 1) {
                        groupPage(1, listOf(group("public", true)), hasNext = true)
                    } else {
                        groupPage(2, listOf(group("private", false)))
                    }
                }
            }
            val enabled = libraryController(enabledClient)
            enabled.initialize(profile(), LibraryBooksEntry.Browse, advancedGroupsEnabled = true)
            advanceUntilIdle()

            assertTrue(enabled.state.value.advancedGroupsEnabled)
            assertEquals(LibraryScope.Global, enabled.state.value.scope)
            assertEquals(
                listOf("public", "private"),
                enabled.state.value.groupSelector.groups.map {
                    it.id
                }
            )
            assertEquals(listOf(1, 2), enabledClient.groupRequests.map { it.page })
        }

    @Test
    fun `group scope resets paging and uses group Books capability`() = runTest {
        val client = FakeLibraryClient().apply {
            groupCall = { groupPage(1, listOf(group("group-1", false))) }
            groupBookCall = { _, options -> page(options.page, listOf("group-book"), 1) }
        }
        val controller = libraryController(client)
        controller.initialize(profile(), LibraryBooksEntry.Browse, advancedGroupsEnabled = true)
        advanceUntilIdle()
        controller.commitSearch("broad metadata")
        advanceUntilIdle()

        controller.selectScope(LibraryScope.Group("group-1"))
        advanceUntilIdle()

        assertEquals(LibraryScope.Group("group-1"), controller.state.value.scope)
        assertEquals(LibraryAxis.BOOKS, controller.state.value.axis)
        assertEquals(1, controller.state.value.books.currentPage)
        assertEquals(listOf("group-book"), controller.state.value.books.books.map { it.id })
        val request = client.groupBookRequests.single()
        assertEquals("group-1", request.first)
        assertEquals("broad metadata", request.second.q)
    }

    @Test
    fun `broad search keeps shared semantics while scope selects endpoint`() = runTest {
        val client = FakeLibraryClient().apply {
            groupCall = { groupPage(1, listOf(group("group-1", false))) }
        }
        val controller = libraryController(client)
        controller.initialize(
            profile(),
            LibraryBooksEntry.BroadSearch("dune"),
            advancedGroupsEnabled = true
        )
        advanceUntilIdle()
        controller.selectScope(LibraryScope.Group("group-1"))
        advanceUntilIdle()
        controller.selectScope(LibraryScope.Global)
        advanceUntilIdle()

        assertEquals(LibraryBooksMode.BROAD_SEARCH, controller.state.value.books.mode)
        assertEquals("dune", controller.state.value.books.committedQuery)
        assertEquals(
            listOf(LibraryScope.Global, LibraryScope.Group("group-1"), LibraryScope.Global),
            client.scopedSearchRequests.map { it.first }
        )
        assertTrue(client.groupBookRequests.isEmpty())
    }

    @Test
    fun `Home broad-search route preserves selected group scope`() = runTest {
        val client = FakeLibraryClient().apply {
            groupCall = { groupPage(1, listOf(group("group-1", false))) }
        }
        val controller = libraryController(client)
        controller.initialize(profile(), LibraryBooksEntry.Browse, advancedGroupsEnabled = true)
        advanceUntilIdle()
        controller.selectScope(LibraryScope.Group("group-1"))
        advanceUntilIdle()
        client.groupBookRequests.clear()

        controller.initialize(
            profile(),
            LibraryBooksEntry.BroadSearch("dune"),
            advancedGroupsEnabled = true
        )
        advanceUntilIdle()

        assertEquals(LibraryScope.Group("group-1"), controller.state.value.scope)
        assertEquals(LibraryAxis.BOOKS, controller.state.value.axis)
        val request = client.scopedSearchRequests.last()
        assertEquals(LibraryScope.Group("group-1"), request.first)
        assertEquals("dune", request.second.q)
        assertEquals(1, client.groupRequests.size)
    }

    @Test
    fun `Authors and Series selection preserves scope without fake data loads`() = runTest {
        val client = FakeLibraryClient().apply {
            groupCall = {
                groupPage(
                    1,
                    listOf(group("group-1", false), group("group-2", false))
                )
            }
        }
        val controller = libraryController(client)
        controller.initialize(profile(), LibraryBooksEntry.Browse, advancedGroupsEnabled = true)
        advanceUntilIdle()
        val bookCalls = client.bookRequests.size

        controller.selectAxis(LibraryAxis.AUTHORS)
        controller.selectScope(LibraryScope.Group("group-1"))
        controller.selectAxis(LibraryAxis.SERIES)
        controller.selectScope(LibraryScope.Group("group-2"))
        advanceUntilIdle()

        assertEquals(LibraryAxis.SERIES, controller.state.value.axis)
        assertEquals(LibraryScope.Group("group-2"), controller.state.value.scope)
        assertEquals(bookCalls, client.bookRequests.size)
        assertTrue(client.groupBookRequests.isEmpty())
        assertEquals(1, controller.state.value.books.currentPage)
    }
}
