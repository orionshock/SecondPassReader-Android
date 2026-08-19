package com.secondpasslibrary.reader.shelves

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
    fun `all three collections retain independent state`() = runTest {
        val capability = RecordingShelvesCapability().apply {
            listCall = { options -> shelfPage(options.page, listOf(shelf(options.scope.name))) }
        }
        val controller = controller(capability, this)
        controller.initialize(shelvesProfile())
        advanceUntilIdle()
        controller.showCollection(ShelvesCollection.SHARED)
        advanceUntilIdle()
        controller.showCollection(ShelvesCollection.GROUP)
        advanceUntilIdle()

        assertEquals(listOf("PERSONAL"), controller.state.value.personal.shelves.map { it.id })
        assertEquals(listOf("SHARED"), controller.state.value.shared.shelves.map { it.id })
        assertEquals(listOf("GROUP"), controller.state.value.group.shelves.map { it.id })
        assertEquals(
            ShelvesDestination.Collection(ShelvesCollection.GROUP),
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

    @Test
    fun `create is available only from Personal collection`() = runTest {
        val capability = RecordingShelvesCapability()
        val controller = controller(capability, this)
        controller.initialize(shelvesProfile())
        advanceUntilIdle()

        controller.openCreate()
        assertTrue(controller.state.value.createOpen)
        controller.dismissCreate()
        controller.showCollection(ShelvesCollection.SHARED)
        controller.openCreate()

        assertFalse(controller.state.value.createOpen)
        assertTrue(capability.createRequests.isEmpty())
    }

    @Test
    fun `successful create refreshes only Personal and leaves siblings untouched`() = runTest {
        val capability = RecordingShelvesCapability().apply {
            listCall = { options -> shelfPage(options.page, listOf(shelf(options.scope.name))) }
            createCall = { shelf("new", canEdit = true) }
        }
        val controller = controller(capability, this)
        controller.initialize(shelvesProfile())
        advanceUntilIdle()
        controller.showCollection(ShelvesCollection.SHARED)
        advanceUntilIdle()
        controller.showCollection(ShelvesCollection.GROUP)
        advanceUntilIdle()
        controller.showCollection(ShelvesCollection.PERSONAL)
        controller.openCreate()
        controller.create.updateName("New shelf")

        controller.submitCreate()
        advanceUntilIdle()

        assertFalse(controller.state.value.createOpen)
        assertEquals(CreatePersonalShelfState(), controller.create.state.value)
        assertEquals(2, capability.listRequests.count { it.scope.name == "PERSONAL" })
        assertEquals(1, capability.listRequests.count { it.scope.name == "SHARED" })
        assertEquals(1, capability.listRequests.count { it.scope.name == "GROUP" })
        assertEquals(listOf("SHARED"), controller.state.value.shared.shelves.map { it.id })
        assertEquals(listOf("GROUP"), controller.state.value.group.shelves.map { it.id })
    }

    private fun controller(capability: RecordingShelvesCapability, scope: CoroutineScope) =
        ShelvesController(ShelvesTestClientProvider(ShelvesTestClient(capability)), scope)
}
