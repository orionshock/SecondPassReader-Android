package com.secondpasslibrary.reader.shelves.detail

import com.secondpasslibrary.client.ShelfItemOrdering
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.shelves.RecordingShelvesCapability
import com.secondpasslibrary.reader.shelves.ShelvesFailure
import com.secondpasslibrary.reader.shelves.ShelvesLoadPhase
import com.secondpasslibrary.reader.shelves.ShelvesTestClient
import com.secondpasslibrary.reader.shelves.ShelvesTestClientProvider
import com.secondpasslibrary.reader.shelves.shelf
import com.secondpasslibrary.reader.shelves.shelfItem
import com.secondpasslibrary.reader.shelves.shelfItemPage
import com.secondpasslibrary.reader.shelves.shelvesProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShelfDetailControllerTest {
    @Test
    fun `detail and non-contiguous items load independently`() = runTest {
        val capability = RecordingShelvesCapability().apply {
            detailCall = { shelf(it, canEdit = true) }
            itemsCall = { _, options ->
                shelfItemPage(options.page, listOf(shelfItem("one", 0), shelfItem("two", 7)))
            }
        }
        val controller = controller(capability, this)
        controller.select("shelf-1")
        advanceUntilIdle()

        assertTrueCanEdit(controller)
        assertEquals(listOf(0, 7), controller.state.value.items.items.map { it.position })
        assertEquals(ShelfItemOrdering.POSITION, capability.itemRequests.single().second.ordering)
    }

    @Test
    fun `detail failure does not discard successful items`() = runTest {
        val capability = RecordingShelvesCapability().apply {
            detailCall = { throw SplClientException.ProtocolInvalid("detail") }
            itemsCall = { _, options -> shelfItemPage(options.page, listOf(shelfItem("one", 3))) }
        }
        val controller = controller(capability, this)
        controller.select("shelf-1")
        advanceUntilIdle()

        assertEquals(ShelvesFailure.PROTOCOL_INVALID, controller.state.value.detail.failure)
        assertEquals(listOf("one"), controller.state.value.items.items.map { it.id })
    }

    @Test
    fun `item failure does not discard successful detail`() = runTest {
        val capability = RecordingShelvesCapability().apply {
            itemsCall = { _, _ -> throw SplClientException.ServerUnreachable() }
        }
        val controller = controller(capability, this)
        controller.select("shelf-1")
        advanceUntilIdle()

        assertNotNull(controller.state.value.detail.shelf)
        assertEquals(ShelvesFailure.UNREACHABLE, controller.state.value.items.error?.failure)
    }

    @Test
    fun `item ordering resets paging without invalidating detail`() = runTest {
        val capability = RecordingShelvesCapability()
        val controller = controller(capability, this)
        controller.select("shelf-1")
        advanceUntilIdle()

        controller.changeItemOrdering(ShelfItemOrdering.TITLE_DESCENDING)
        advanceUntilIdle()

        assertEquals(
            ShelfItemOrdering.TITLE_DESCENDING,
            capability.itemRequests.last().second.ordering
        )
        assertEquals(1, capability.itemRequests.last().second.page)
        assertNotNull(controller.state.value.detail.shelf)
    }

    @Test
    fun `Book layout survives detail navigation without changing requests`() = runTest {
        val capability = RecordingShelvesCapability()
        val controller = controller(capability, this)
        controller.select("shelf-1")
        advanceUntilIdle()

        controller.setLayout(ShelfBooksLayout.LIST)
        controller.clear()
        controller.select("shelf-2")
        advanceUntilIdle()

        assertEquals(ShelfBooksLayout.LIST, controller.state.value.items.layout)
        assertEquals(2, capability.itemRequests.size)
    }

    @Test
    fun `item paging suppresses duplicate request and appends`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val capability = RecordingShelvesCapability().apply {
            itemsCall = { _, options ->
                if (options.page == 1) {
                    shelfItemPage(1, listOf(shelfItem("first", 2)), total = 2, hasNext = true)
                } else {
                    gate.await()
                    shelfItemPage(2, listOf(shelfItem("second", 9)), total = 2)
                }
            }
        }
        val controller = controller(capability, this)
        controller.select("shelf-1")
        advanceUntilIdle()
        controller.loadNextPage()
        controller.loadNextPage()
        runCurrent()
        assertEquals(listOf(1, 2), capability.itemRequests.map { it.second.page })
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("first", "second"), controller.state.value.items.items.map { it.id })
        assertNull(controller.state.value.items.error)
    }

    @Test
    fun `item append failure retains items and retry appends`() = runTest {
        var attempts = 0
        val capability = RecordingShelvesCapability().apply {
            itemsCall = { _, options ->
                if (options.page == 1) {
                    shelfItemPage(1, listOf(shelfItem("first", 4)), total = 2, hasNext = true)
                } else {
                    attempts += 1
                    if (attempts == 1) throw SplClientException.ServerUnreachable()
                    shelfItemPage(2, listOf(shelfItem("second", 11)), total = 2)
                }
            }
        }
        val controller = controller(capability, this)
        controller.select("shelf-1")
        advanceUntilIdle()
        controller.loadNextPage()
        advanceUntilIdle()

        assertEquals(listOf("first"), controller.state.value.items.items.map { it.id })
        assertEquals(ShelvesLoadPhase.NEXT_PAGE, controller.state.value.items.error?.phase)

        controller.retryItems()
        advanceUntilIdle()
        assertEquals(listOf("first", "second"), controller.state.value.items.items.map { it.id })
    }

    private fun controller(capability: RecordingShelvesCapability, scope: CoroutineScope) =
        ShelfDetailController(
            ShelvesTestClientProvider(ShelvesTestClient(capability)),
            scope
        ).also { it.prepare(shelvesProfile()) }

    private fun assertTrueCanEdit(controller: ShelfDetailController) {
        assertEquals(true, controller.state.value.detail.shelf?.canEdit)
    }
}
