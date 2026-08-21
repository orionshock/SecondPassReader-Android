package com.secondpasslibrary.reader.shelves.editor

import com.secondpasslibrary.client.ShelfItemMove
import com.secondpasslibrary.client.ShelfMutationRejection
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.shelves.RecordingShelvesCapability
import com.secondpasslibrary.reader.shelves.ShelvesTestClient
import com.secondpasslibrary.reader.shelves.ShelvesTestClientProvider
import com.secondpasslibrary.reader.shelves.availableEditorItem
import com.secondpasslibrary.reader.shelves.shelf
import com.secondpasslibrary.reader.shelves.shelfEditorPage
import com.secondpasslibrary.reader.shelves.shelfItem
import com.secondpasslibrary.reader.shelves.shelvesProfile
import com.secondpasslibrary.reader.shelves.unavailableEditorItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShelfContentsEditorControllerTest {
    @Test
    fun `editor loads both entry kinds counts and non-contiguous positions`() = runTest {
        val capability = RecordingShelvesCapability().apply {
            editorCall = { _, options ->
                shelfEditorPage(
                    options.page,
                    listOf(availableEditorItem("visible", 2), unavailableEditorItem("hidden", 8))
                )
            }
        }
        val controller = controller(capability, this)
        controller.open("shelf-1")
        advanceUntilIdle()

        assertEquals(listOf(2, 8), controller.state.value.entries.map { it.position })
        assertEquals(1, controller.state.value.visibleItemCount)
        assertEquals(1, controller.state.value.unavailableItemCount)
        assertFalse(controller.state.value.directPositionAvailable)
    }

    @Test
    fun `relative moves use item identity and reconcile canonical order`() = runTest {
        var results = listOf(availableEditorItem("one", 0), availableEditorItem("two", 1))
        val reconciled = mutableListOf<String>()
        val capability = RecordingShelvesCapability().apply {
            editorCall = { _, options -> shelfEditorPage(options.page, results) }
            moveCall = { _, _, direction ->
                if (direction == ShelfItemMove.DOWN) results = results.reversed()
                shelfItem("one", 1)
            }
        }
        val controller = controller(capability, this) { reconciled += it.id }
        controller.open("shelf-1")
        advanceUntilIdle()
        controller.move("one", ShelfItemMove.DOWN)
        advanceUntilIdle()
        controller.move("one", ShelfItemMove.UP)
        advanceUntilIdle()

        assertEquals(
            listOf(ShelfItemMove.DOWN, ShelfItemMove.UP),
            capability.moveRequests.map { it.third }
        )
        assertEquals(listOf("two", "one"), controller.state.value.entries.map { it.id })
        assertEquals(listOf("shelf-1", "shelf-1"), reconciled)
        assertNull(controller.state.value.mutation.failure)
    }

    @Test
    fun `page edge does not disable server-aware relative movement`() = runTest {
        val capability = RecordingShelvesCapability().apply {
            editorCall = { _, options ->
                shelfEditorPage(
                    options.page,
                    listOf(availableEditorItem("edge", 49)),
                    total = 51,
                    hasNext = true
                )
            }
        }
        val controller = controller(capability, this)
        controller.open("shelf-1")
        advanceUntilIdle()
        controller.move("edge", ShelfItemMove.DOWN)
        advanceUntilIdle()

        assertEquals("edge", capability.moveRequests.single().second)
    }

    @Test
    fun `editor paging appends in canonical stored order`() = runTest {
        val capability = RecordingShelvesCapability().apply {
            editorCall = { _, options ->
                if (options.page == 1) {
                    shelfEditorPage(
                        1,
                        listOf(availableEditorItem("first", 4)),
                        total = 2,
                        hasNext = true
                    )
                } else {
                    shelfEditorPage(2, listOf(unavailableEditorItem("second", 9)), total = 2)
                }
            }
        }
        val controller = controller(capability, this)
        controller.open("shelf-1")
        advanceUntilIdle()
        controller.loadNextPage()
        advanceUntilIdle()

        assertEquals(listOf("first", "second"), controller.state.value.entries.map { it.id })
        assertEquals(listOf(4, 9), controller.state.value.entries.map { it.position })
    }

    @Test
    fun `direct position converts one-based input to zero-based wire value`() = runTest {
        val items = listOf(availableEditorItem("one", 0), availableEditorItem("two", 1))
        val capability = RecordingShelvesCapability().apply {
            editorCall = { _, options -> shelfEditorPage(options.page, items) }
        }
        val controller = controller(capability, this)
        controller.open("shelf-1")
        advanceUntilIdle()
        controller.openPosition("one")
        controller.updatePosition("2")
        controller.submitPosition()
        advanceUntilIdle()

        assertEquals(1, capability.positionRequests.single().third)
    }

    @Test
    fun `direct position is unavailable when retained entries are unavailable`() = runTest {
        val capability = RecordingShelvesCapability().apply {
            editorCall = { _, options ->
                shelfEditorPage(
                    options.page,
                    listOf(availableEditorItem("one", 0)),
                    unavailable = 1
                )
            }
        }
        val controller = controller(capability, this)
        controller.open("shelf-1")
        advanceUntilIdle()
        controller.openPosition("one")

        assertNull(controller.state.value.positionDialog)
        assertTrue(capability.positionRequests.isEmpty())
    }

    @Test
    fun `direct position server failure retains editor content`() = runTest {
        val items = listOf(availableEditorItem("one", 0), availableEditorItem("two", 1))
        val capability = RecordingShelvesCapability().apply {
            editorCall = { _, options -> shelfEditorPage(options.page, items) }
            positionCall = { _, _, _ ->
                throw SplClientException.ShelfMutationRejected(
                    ShelfMutationRejection.DIRECT_POSITION_UNAVAILABLE
                )
            }
        }
        val controller = controller(capability, this)
        controller.open("shelf-1")
        advanceUntilIdle()
        controller.openPosition("one")
        controller.updatePosition("2")
        controller.submitPosition()
        advanceUntilIdle()

        assertEquals(listOf("one", "two"), controller.state.value.entries.map { it.id })
        assertEquals(
            ShelfContentsMutationFailure.DIRECT_POSITION_UNAVAILABLE,
            controller.state.value.mutation.failure
        )
    }

    @Test
    fun `removal supports available and unavailable item IDs and reconciles`() = runTest {
        var items = listOf(availableEditorItem("visible", 0), unavailableEditorItem("hidden", 4))
        val capability = RecordingShelvesCapability().apply {
            editorCall = { _, options -> shelfEditorPage(options.page, items) }
            removeCall = { _, itemId -> items = items.filterNot { it.id == itemId } }
        }
        val controller = controller(capability, this)
        controller.open("shelf-1")
        advanceUntilIdle()
        controller.requestRemoval("hidden")
        controller.confirmRemoval()
        advanceUntilIdle()
        controller.requestRemoval("visible")
        controller.confirmRemoval()
        advanceUntilIdle()

        assertEquals(listOf("hidden", "visible"), capability.removeRequests.map { it.second })
        assertTrue(controller.state.value.entries.isEmpty())
    }

    @Test
    fun `failed removal is non-idempotent and retains content`() = runTest {
        val items = listOf(unavailableEditorItem("hidden", 3))
        val capability = RecordingShelvesCapability().apply {
            editorCall = { _, options -> shelfEditorPage(options.page, items) }
            removeCall = { _, _ ->
                throw SplClientException.ShelfMutationRejected(
                    ShelfMutationRejection.RESOURCE_NOT_FOUND
                )
            }
        }
        val controller = controller(capability, this)
        controller.open("shelf-1")
        advanceUntilIdle()
        controller.requestRemoval("hidden")
        controller.confirmRemoval()
        advanceUntilIdle()

        assertEquals(listOf("hidden"), controller.state.value.entries.map { it.id })
        assertEquals(
            ShelfContentsMutationFailure.NOT_FOUND,
            controller.state.value.mutation.failure
        )
    }

    @Test
    fun `successful mutation with failed reconcile retains content and reports uncertainty`() =
        runTest {
            var editorCalls = 0
            val items = listOf(availableEditorItem("one", 0))
            val capability = RecordingShelvesCapability().apply {
                editorCall = { _, options ->
                    editorCalls += 1
                    if (editorCalls > 1) throw SplClientException.ServerUnreachable()
                    shelfEditorPage(options.page, items)
                }
            }
            val controller = controller(capability, this)
            controller.open("shelf-1")
            advanceUntilIdle()
            controller.move("one", ShelfItemMove.UP)
            advanceUntilIdle()

            assertEquals(listOf("one"), controller.state.value.entries.map { it.id })
            assertEquals(
                ShelfContentsMutationFailure.RECONCILE_FAILED,
                controller.state.value.mutation.failure
            )
        }

    private fun controller(
        capability: RecordingShelvesCapability,
        scope: CoroutineScope,
        reconciled: (com.secondpasslibrary.client.Shelf) -> Unit = {}
    ) = ShelfContentsEditorController(
        ShelvesTestClientProvider(ShelvesTestClient(capability)),
        scope,
        reconciled
    ).also { it.prepare(shelvesProfile()) }
}
