package com.secondpasslibrary.reader.library.axis

import com.secondpasslibrary.client.AuthorOrdering
import com.secondpasslibrary.client.LibraryScope
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.library.DEFAULT_LIBRARY_PAGE_SIZE
import com.secondpasslibrary.reader.library.LibraryFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryAuthorsControllerTest {
    @Test
    fun `initial load uses name order page size and bounded previews`() = runTest {
        val client = FakeLibraryAxisClient().apply {
            authorList = { axisPage(1, listOf(author("b"), author("a")), 8, hasNext = true) }
        }
        val controller = controller(client)

        controller.prepare(libraryProfile(), LibraryScope.Global)
        controller.activate()
        advanceUntilIdle()

        assertEquals(listOf("b", "a"), controller.state.value.items.map { it.id })
        assertEquals(AuthorOrdering.NAME, client.authorRequests.single().ordering)
        assertEquals(DEFAULT_LIBRARY_PAGE_SIZE, client.authorRequests.single().pageSize)
        assertEquals(LIBRARY_AXIS_PREVIEW_LIMIT, client.authorRequests.single().previewLimit)
        assertTrue(controller.state.value.hasNext)
    }

    @Test
    fun `committed search and ordering reset to page one`() = runTest {
        val client = FakeLibraryAxisClient()
        val controller = controller(client)
        controller.prepare(libraryProfile(), LibraryScope.Global)
        controller.activate()
        advanceUntilIdle()

        controller.commitSearch("octavia")
        advanceUntilIdle()
        controller.changeOrdering(AuthorOrdering.BOOK_COUNT_DESCENDING)
        advanceUntilIdle()

        assertEquals("octavia", controller.state.value.committedQuery)
        assertEquals(AuthorOrdering.BOOK_COUNT_DESCENDING, controller.state.value.ordering)
        assertEquals(1, client.authorRequests.last().page)
        assertEquals("octavia", client.authorRequests.last().q)
    }

    @Test
    fun `next page appends and duplicate request is suppressed`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val client = FakeLibraryAxisClient().apply {
            authorList = { request ->
                if (request.page == 1) {
                    axisPage(1, listOf(author("first")), 2, hasNext = true)
                } else {
                    gate.await()
                    axisPage(2, listOf(author("second")), 2)
                }
            }
        }
        val controller = controller(client)
        controller.prepare(libraryProfile(), LibraryScope.Global)
        controller.activate()
        advanceUntilIdle()

        controller.loadNextPage()
        controller.loadNextPage()
        runCurrent()
        assertEquals(listOf(1, 2), client.authorRequests.map { it.page })
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("first", "second"), controller.state.value.items.map { it.id })
    }

    @Test
    fun `stale search response is rejected`() = runTest {
        val staleGate = CompletableDeferred<Unit>()
        val client = FakeLibraryAxisClient().apply {
            authorList = { request ->
                when (request.q) {
                    "old" -> withContext(NonCancellable) {
                        staleGate.await()
                        axisPage(1, listOf(author("stale")))
                    }

                    "new" -> axisPage(1, listOf(author("current")))

                    else -> axisPage(1, emptyList())
                }
            }
        }
        val controller = controller(client)
        controller.prepare(libraryProfile(), LibraryScope.Global)
        controller.activate()
        advanceUntilIdle()

        controller.commitSearch("old")
        runCurrent()
        controller.commitSearch("new")
        runCurrent()
        staleGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("current"), controller.state.value.items.map { it.id })
    }

    @Test
    fun `group scope uses group Authors and clears selected detail`() = runTest {
        val client = FakeLibraryAxisClient().apply {
            groupAuthorList = { _, options -> axisPage(options.page, listOf(author("group"))) }
        }
        val controller = controller(client)
        controller.prepare(libraryProfile(), LibraryScope.Global)
        controller.activate()
        advanceUntilIdle()
        controller.selectAuthor("old")
        advanceUntilIdle()

        controller.selectScope(LibraryScope.Group("group-1"), activate = true)
        advanceUntilIdle()

        assertEquals("group-1", client.groupAuthorRequests.single().first)
        assertEquals(listOf("group"), controller.state.value.items.map { it.id })
        assertNull(controller.state.value.selected)
    }

    @Test
    fun `selected Author detail and explicit detail failure are retained in state`() = runTest {
        val client = FakeLibraryAxisClient()
        val controller = controller(client)
        controller.prepare(libraryProfile(), LibraryScope.Global)
        controller.selectAuthor("good")
        advanceUntilIdle()

        assertEquals("Biography good", controller.state.value.selected?.detail?.biography)
        assertEquals(
            LIBRARY_ENTITY_DETAIL_PREVIEW_LIMIT,
            client.authorDetailRequests.single().second.previewLimit
        )

        client.authorDetail = { throw SplClientException.ProtocolInvalid("author") }
        controller.selectAuthor("bad")
        advanceUntilIdle()

        val selected = controller.state.value.selected
        assertEquals("bad", selected?.id)
        assertEquals(LibraryFailure.PROTOCOL_INVALID, selected?.failure)
        assertFalse(selected?.loading ?: true)
    }

    @Test
    fun `next page failure retains Authors and retry appends`() = runTest {
        var attempts = 0
        val client = FakeLibraryAxisClient().apply {
            authorList = { request ->
                if (request.page == 1) {
                    axisPage(1, listOf(author("first")), 2, hasNext = true)
                } else {
                    attempts += 1
                    if (attempts == 1) throw SplClientException.ServerUnreachable()
                    axisPage(2, listOf(author("second")), 2)
                }
            }
        }
        val controller = controller(client)
        controller.prepare(libraryProfile(), LibraryScope.Global)
        controller.activate()
        advanceUntilIdle()
        controller.loadNextPage()
        advanceUntilIdle()

        assertEquals(listOf("first"), controller.state.value.items.map { it.id })
        assertEquals(PagedLibraryAxisLoadPhase.NEXT_PAGE, controller.state.value.error?.phase)
        controller.retry()
        advanceUntilIdle()

        assertEquals(listOf("first", "second"), controller.state.value.items.map { it.id })
        assertNull(controller.state.value.error)
    }

    private fun kotlinx.coroutines.test.TestScope.controller(client: FakeLibraryAxisClient) =
        LibraryAuthorsController(FakeLibraryAxisClientProvider(client), this)
}
