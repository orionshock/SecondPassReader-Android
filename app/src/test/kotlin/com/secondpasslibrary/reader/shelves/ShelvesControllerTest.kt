package com.secondpasslibrary.reader.shelves

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShelvesControllerTest {
    @Test
    fun `selection routes through parent and back restores Personal state`() = runTest {
        val capability = RecordingShelvesCapability().apply {
            listCall = { options -> shelfPage(options.page, listOf(shelf("personal"))) }
        }
        val controller = controller(capability, this)
        controller.initialize(shelvesProfile())
        advanceUntilIdle()

        controller.selectShelf("personal")
        advanceUntilIdle()
        assertEquals(
            ShelvesDestination.Detail("personal", ShelvesCollection.PERSONAL),
            controller.state.value.destination
        )

        controller.backFromDetail()
        assertEquals(
            ShelvesDestination.Collection(ShelvesCollection.PERSONAL),
            controller.state.value.destination
        )
        assertEquals(listOf("personal"), controller.state.value.personal.shelves.map { it.id })
        assertNull(controller.state.value.detail.shelfId)
        assertEquals(1, capability.listRequests.size)
    }

    @Test
    fun `Shared navigation leaves Personal child untouched`() = runTest {
        val capability = RecordingShelvesCapability().apply {
            listCall = { options -> shelfPage(options.page, listOf(shelf(options.scope.name))) }
        }
        val controller = controller(capability, this)
        controller.initialize(shelvesProfile())
        advanceUntilIdle()
        controller.showCollection(ShelvesCollection.SHARED)
        advanceUntilIdle()

        assertEquals(listOf("PERSONAL"), controller.state.value.personal.shelves.map { it.id })
        assertEquals(listOf("SHARED"), controller.state.value.shared.shelves.map { it.id })
        assertEquals(
            ShelvesDestination.Collection(ShelvesCollection.SHARED),
            controller.state.value.destination
        )
    }

    @Test
    fun `back from Shared detail restores Shared collection without reload`() = runTest {
        val capability = RecordingShelvesCapability()
        val controller = controller(capability, this)
        controller.initialize(shelvesProfile())
        advanceUntilIdle()
        controller.showCollection(ShelvesCollection.SHARED)
        advanceUntilIdle()
        controller.selectShelf("shared")
        advanceUntilIdle()

        controller.backFromDetail()

        assertEquals(
            ShelvesDestination.Collection(ShelvesCollection.SHARED),
            controller.state.value.destination
        )
        assertEquals(2, capability.listRequests.size)
    }

    private fun controller(capability: RecordingShelvesCapability, scope: CoroutineScope) =
        ShelvesController(ShelvesTestClientProvider(ShelvesTestClient(capability)), scope)
}
