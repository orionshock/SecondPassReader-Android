package com.secondpasslibrary.reader.shelves

import com.secondpasslibrary.reader.shelves.collection.ShelfCollectionChange
import com.secondpasslibrary.reader.shelves.management.CreatePersonalShelfState
import com.secondpasslibrary.reader.shelves.management.ShelfManagementFailure
import com.secondpasslibrary.reader.shelves.management.canManageShelf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    fun `re-pairing resets navigation and reloads Personal Shelves`() = runTest {
        val capability = RecordingShelvesCapability().apply {
            listCall = { options -> shelfPage(options.page, listOf(shelf("personal"))) }
        }
        val controller = controller(capability, this)
        val profile = shelvesProfile()
        controller.initialize(profile)
        advanceUntilIdle()
        controller.selectShelf("personal")
        advanceUntilIdle()

        controller.initialize(profile.copy(clientSessionId = "replacement-session"))
        advanceUntilIdle()

        assertEquals(
            ShelvesDestination.Collection(ShelvesCollection.PERSONAL),
            controller.state.value.destination
        )
        assertEquals(2, capability.listRequests.count { it.scope.name == "PERSONAL" })
    }

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

    @Test
    fun `management policy requires editable Personal Shelf`() {
        assertTrue(canManageShelf(ShelvesCollection.PERSONAL, shelf("one", canEdit = true)))
        assertFalse(canManageShelf(ShelvesCollection.PERSONAL, shelf("one", canEdit = false)))
        assertFalse(canManageShelf(ShelvesCollection.SHARED, shelf("one", canEdit = true)))
        assertFalse(canManageShelf(ShelvesCollection.GROUP, shelf("one", canEdit = true)))
    }

    @Test
    fun `successful edit updates detail and Personal only`() = runTest {
        val original = shelf("personal", canEdit = true)
        val capability = RecordingShelvesCapability().apply {
            listCall = { options -> shelfPage(1, listOf(shelf(options.scope.name))) }
            detailCall = { original }
            updateCall = { _, _ ->
                original.copy(
                    name = "Renamed",
                    visibility = com.secondpasslibrary.client.ShelfVisibility.LISTED
                )
            }
        }
        val controller = controller(capability, this)
        controller.initialize(shelvesProfile())
        advanceUntilIdle()
        controller.personal.applyAuthoritativeChange(ShelfCollectionChange.Added(original))
        controller.showCollection(ShelvesCollection.SHARED)
        advanceUntilIdle()
        controller.showCollection(ShelvesCollection.GROUP)
        advanceUntilIdle()
        controller.showCollection(ShelvesCollection.PERSONAL)
        controller.selectShelf(original.id)
        advanceUntilIdle()
        val sharedBefore = controller.state.value.shared
        val groupBefore = controller.state.value.group

        controller.openEdit()
        controller.edit.updateName("Renamed")
        controller.submitEdit()
        advanceUntilIdle()

        assertEquals("Renamed", controller.state.value.detail.detail.shelf?.name)
        assertEquals(
            "Renamed",
            controller.state.value.personal.shelves.first { it.id == original.id }.name
        )
        assertEquals(sharedBefore, controller.state.value.shared)
        assertEquals(groupBefore, controller.state.value.group)
        assertEquals(
            ShelvesDestination.Detail(original.id, ShelvesCollection.PERSONAL),
            controller.state.value.destination
        )
    }

    @Test
    fun `successful delete removes Personal Shelf and returns without touching siblings`() =
        runTest {
            val original = shelf("personal", canEdit = true)
            val capability = RecordingShelvesCapability().apply {
                listCall = { options -> shelfPage(1, listOf(shelf(options.scope.name))) }
                detailCall = { original }
            }
            val controller = controller(capability, this)
            controller.initialize(shelvesProfile())
            advanceUntilIdle()
            controller.personal.applyAuthoritativeChange(ShelfCollectionChange.Added(original))
            controller.showCollection(ShelvesCollection.SHARED)
            advanceUntilIdle()
            controller.showCollection(ShelvesCollection.GROUP)
            advanceUntilIdle()
            controller.showCollection(ShelvesCollection.PERSONAL)
            controller.selectShelf(original.id)
            advanceUntilIdle()
            val sharedBefore = controller.state.value.shared
            val groupBefore = controller.state.value.group

            controller.openDelete()
            assertTrue(controller.delete.state.value.open)
            controller.confirmDelete()
            advanceUntilIdle()

            assertEquals(listOf(original.id), capability.deleteRequests)
            assertFalse(controller.state.value.personal.shelves.any { it.id == original.id })
            assertEquals(sharedBefore, controller.state.value.shared)
            assertEquals(groupBefore, controller.state.value.group)
            assertEquals(
                ShelvesDestination.Collection(ShelvesCollection.PERSONAL),
                controller.state.value.destination
            )
        }

    @Test
    fun `failed delete keeps Shelf Detail context`() = runTest {
        val original = shelf("personal", canEdit = true)
        val capability = RecordingShelvesCapability().apply {
            detailCall = { original }
            deleteCall =
                { throw com.secondpasslibrary.client.SplClientException.ServerUnreachable() }
        }
        val controller = controller(capability, this)
        controller.initialize(shelvesProfile())
        advanceUntilIdle()
        controller.selectShelf(original.id)
        advanceUntilIdle()

        controller.openDelete()
        controller.confirmDelete()
        advanceUntilIdle()

        assertEquals(
            ShelvesDestination.Detail(original.id, ShelvesCollection.PERSONAL),
            controller.state.value.destination
        )
        assertEquals(ShelfManagementFailure.UNREACHABLE, controller.delete.state.value.failure)
    }

    @Test
    fun `Personal editor mutation reconciles detail and My Shelves only`() = runTest {
        val original = shelf("personal", canEdit = true)
        val updated = original.copy(itemCount = 1, previewBooks = emptyList())
        var mutated = false
        val capability = RecordingShelvesCapability().apply {
            listCall = { options -> shelfPage(1, listOf(shelf(options.scope.name))) }
            detailCall = { if (mutated) updated else original }
            editorCall = { _, options ->
                val entries = if (mutated) emptyList() else listOf(availableEditorItem("item", 0))
                shelfEditorPage(options.page, entries)
            }
            removeCall = { _, _ -> mutated = true }
        }
        val controller = controller(capability, this)
        controller.initialize(shelvesProfile())
        advanceUntilIdle()
        controller.personal.applyAuthoritativeChange(ShelfCollectionChange.Added(original))
        controller.showCollection(ShelvesCollection.SHARED)
        advanceUntilIdle()
        controller.showCollection(ShelvesCollection.GROUP)
        advanceUntilIdle()
        controller.showCollection(ShelvesCollection.PERSONAL)
        controller.selectShelf(original.id)
        advanceUntilIdle()
        val sharedBefore = controller.state.value.shared
        val groupBefore = controller.state.value.group

        controller.openContentsEditor()
        advanceUntilIdle()
        controller.editor.requestRemoval("item")
        controller.editor.confirmRemoval()
        advanceUntilIdle()

        assertEquals(1, controller.state.value.detail.detail.shelf?.itemCount)
        assertEquals(
            1,
            controller.state.value.personal.shelves.first { it.id == original.id }.itemCount
        )
        assertEquals(sharedBefore, controller.state.value.shared)
        assertEquals(groupBefore, controller.state.value.group)
        assertEquals(
            ShelvesDestination.ContentsEditor(original.id, ShelvesCollection.PERSONAL),
            controller.state.value.destination
        )
        controller.backFromContentsEditor()
        assertEquals(
            ShelvesDestination.Detail(original.id, ShelvesCollection.PERSONAL),
            controller.state.value.destination
        )
    }

    @Test
    fun `Shared and Group Shelf details cannot enter contents editor`() = runTest {
        val editableResponse = shelf("foreign", canEdit = true)
        val capability = RecordingShelvesCapability().apply { detailCall = { editableResponse } }
        val controller = controller(capability, this)
        controller.initialize(shelvesProfile())
        advanceUntilIdle()

        listOf(ShelvesCollection.SHARED, ShelvesCollection.GROUP).forEach { collection ->
            controller.showCollection(collection)
            advanceUntilIdle()
            controller.selectShelf("foreign")
            advanceUntilIdle()
            controller.openContentsEditor()
            assertEquals(
                ShelvesDestination.Detail("foreign", collection),
                controller.state.value.destination
            )
            controller.backFromDetail()
        }
        assertTrue(capability.editorRequests.isEmpty())
    }

    private fun controller(capability: RecordingShelvesCapability, scope: TestScope) =
        ShelvesController(
            ShelvesTestClientProvider(ShelvesTestClient(capability)),
            CoroutineScope(
                scope.backgroundScope.coroutineContext +
                    UnconfinedTestDispatcher(scope.testScheduler)
            )
        )
}
