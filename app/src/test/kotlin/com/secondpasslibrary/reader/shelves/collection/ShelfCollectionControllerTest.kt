package com.secondpasslibrary.reader.shelves.collection

import com.secondpasslibrary.client.ShelfOrdering
import com.secondpasslibrary.client.ShelfScope
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.shelves.RecordingShelvesCapability
import com.secondpasslibrary.reader.shelves.SHELF_CARD_PREVIEW_LIMIT
import com.secondpasslibrary.reader.shelves.SHELVES_PAGE_SIZE
import com.secondpasslibrary.reader.shelves.ShelvesLoadPhase
import com.secondpasslibrary.reader.shelves.ShelvesTestClient
import com.secondpasslibrary.reader.shelves.ShelvesTestClientProvider
import com.secondpasslibrary.reader.shelves.shelf
import com.secondpasslibrary.reader.shelves.shelfPage
import com.secondpasslibrary.reader.shelves.shelvesProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShelfCollectionControllerTest {
    @Test
    fun `Personal initial load uses stable paging and preview request`() = runTest {
        val capability = RecordingShelvesCapability().apply {
            listCall = { shelfPage(1, listOf(shelf("editable", canEdit = true))) }
        }
        val controller = PersonalShelvesController(provider(capability), this)

        controller.prepare(shelvesProfile())
        controller.activate()
        advanceUntilIdle()

        val request = capability.listRequests.single()
        assertEquals(ShelfScope.PERSONAL, request.scope)
        assertEquals(ShelfOrdering.NAME, request.ordering)
        assertEquals(SHELVES_PAGE_SIZE, request.pageSize)
        assertEquals(SHELF_CARD_PREVIEW_LIMIT, request.previewLimit)
        assertTrue(controller.state.value.shelves.single().canEdit)
    }

    @Test
    fun `Shared uses independent state and ordering reset`() = runTest {
        val capability = RecordingShelvesCapability()
        val personal = PersonalShelvesController(provider(capability), this)
        val shared = SharedShelvesController(provider(capability), this)
        personal.prepare(shelvesProfile())
        shared.prepare(shelvesProfile())

        personal.activate()
        advanceUntilIdle()
        shared.activate()
        advanceUntilIdle()
        shared.changeOrdering(ShelfOrdering.ITEM_COUNT_DESCENDING)
        advanceUntilIdle()

        assertEquals(ShelfOrdering.NAME, personal.state.value.ordering)
        assertEquals(ShelfOrdering.ITEM_COUNT_DESCENDING, shared.state.value.ordering)
        assertEquals(ShelfScope.SHARED, capability.listRequests.last().scope)
        assertEquals(1, capability.listRequests.last().page)
    }

    @Test
    fun `Group collection uses group scope independently`() = runTest {
        val capability = RecordingShelvesCapability()
        val controller = GroupShelvesController(provider(capability), this)
        controller.prepare(shelvesProfile())

        controller.activate()
        advanceUntilIdle()

        assertEquals(ShelfScope.GROUP, capability.listRequests.single().scope)
        assertEquals(ShelfOrdering.NAME, controller.state.value.ordering)
    }

    @Test
    fun `next page appends once and preserves server order`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val capability = RecordingShelvesCapability().apply {
            listCall = { request ->
                if (request.page == 1) {
                    shelfPage(1, listOf(shelf("b")), total = 2, hasNext = true)
                } else {
                    gate.await()
                    shelfPage(2, listOf(shelf("a")), total = 2)
                }
            }
        }
        val controller = PersonalShelvesController(provider(capability), this)
        controller.prepare(shelvesProfile())
        controller.activate()
        advanceUntilIdle()

        controller.loadNextPage()
        controller.loadNextPage()
        runCurrent()
        assertEquals(listOf(1, 2), capability.listRequests.map { it.page })
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("b", "a"), controller.state.value.shelves.map { it.id })
    }

    @Test
    fun `append failure retains content and retry appends`() = runTest {
        var attempts = 0
        val capability = RecordingShelvesCapability().apply {
            listCall = { request ->
                if (request.page == 1) {
                    shelfPage(1, listOf(shelf("first")), total = 2, hasNext = true)
                } else {
                    attempts += 1
                    if (attempts == 1) throw SplClientException.ServerUnreachable()
                    shelfPage(2, listOf(shelf("second")), total = 2)
                }
            }
        }
        val controller = PersonalShelvesController(provider(capability), this)
        controller.prepare(shelvesProfile())
        controller.activate()
        advanceUntilIdle()
        controller.loadNextPage()
        advanceUntilIdle()

        assertEquals(listOf("first"), controller.state.value.shelves.map { it.id })
        assertEquals(ShelvesLoadPhase.NEXT_PAGE, controller.state.value.error?.phase)
        assertFalse(controller.state.value.nextPageLoading)

        controller.retry()
        advanceUntilIdle()
        assertEquals(listOf("first", "second"), controller.state.value.shelves.map { it.id })
    }

    private fun provider(capability: RecordingShelvesCapability) =
        ShelvesTestClientProvider(ShelvesTestClient(capability))
}
