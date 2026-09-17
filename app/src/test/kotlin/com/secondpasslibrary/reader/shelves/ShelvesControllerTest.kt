package com.secondpasslibrary.reader.shelves

import com.secondpasslibrary.reader.shelves.management.CreatePersonalShelfState
import com.secondpasslibrary.reader.shelves.management.ShelfManagementFailure
import com.secondpasslibrary.reader.shelves.management.canManageShelf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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
        controller.accept(ShelvesIntent.SelectShelf("personal"))
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

        controller.accept(ShelvesIntent.SelectShelf("personal"))
        advanceUntilIdle()
        assertEquals(
            ShelvesDestination.Detail("personal", ShelvesCollection.PERSONAL),
            controller.state.value.destination
        )

        controller.accept(ShelvesIntent.BackFromDetail)
        assertEquals(
            ShelvesDestination.Collection(ShelvesCollection.PERSONAL),
            controller.state.value.destination
        )
        assertEquals(listOf("personal"), controller.state.value.personal.shelves.map { it.id })
        assertNull(controller.state.value.detail.shelfId)
        assertEquals(1, capability.listRequests.size)
    }

    @Test
    fun `typed entry opens existing Shelf Detail without collection selection`() = runTest {
        val capability = RecordingShelvesCapability()
        val controller = controller(capability, this)
        controller.initialize(shelvesProfile())

        controller.accept(
            ShelvesIntent.OpenShelf(ShelfDetailEntry("shared-1", ShelvesCollection.SHARED))
        )
        advanceUntilIdle()

        assertEquals(
            ShelvesDestination.Detail("shared-1", ShelvesCollection.SHARED),
            controller.state.value.destination
        )
        assertEquals("shared-1", controller.state.value.detail.shelfId)
    }

    @Test
    fun `Shared navigation leaves Personal child untouched`() = runTest {
        val capability = RecordingShelvesCapability().apply {
            listCall = { options -> shelfPage(options.page, listOf(shelf(options.scope.name))) }
        }
        val controller = controller(capability, this)
        controller.initialize(shelvesProfile())
        advanceUntilIdle()
        controller.accept(ShelvesIntent.ShowCollection(ShelvesCollection.SHARED))
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
        controller.accept(ShelvesIntent.ShowCollection(ShelvesCollection.SHARED))
        advanceUntilIdle()
        controller.accept(ShelvesIntent.ShowCollection(ShelvesCollection.GROUP))
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
        controller.accept(ShelvesIntent.ShowCollection(ShelvesCollection.SHARED))
        advanceUntilIdle()
        controller.accept(ShelvesIntent.SelectShelf("shared"))
        advanceUntilIdle()

        controller.accept(ShelvesIntent.BackFromDetail)

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

        controller.accept(ShelvesIntent.OpenCreate)
        assertTrue(controller.state.value.createOpen)
        controller.accept(ShelvesIntent.DismissCreate)
        controller.accept(ShelvesIntent.ShowCollection(ShelvesCollection.SHARED))
        controller.accept(ShelvesIntent.OpenCreate)

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
        controller.accept(ShelvesIntent.ShowCollection(ShelvesCollection.SHARED))
        advanceUntilIdle()
        controller.accept(ShelvesIntent.ShowCollection(ShelvesCollection.GROUP))
        advanceUntilIdle()
        controller.accept(ShelvesIntent.ShowCollection(ShelvesCollection.PERSONAL))
        controller.accept(ShelvesIntent.OpenCreate)
        controller.accept(ShelvesIntent.UpdateCreateName("New shelf"))

        controller.accept(ShelvesIntent.SubmitCreate)
        advanceUntilIdle()

        assertFalse(controller.state.value.createOpen)
        assertEquals(CreatePersonalShelfState(), controller.state.value.create)
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
            listCall = { options ->
                val listed = if (options.scope.name ==
                    "PERSONAL"
                ) {
                    original
                } else {
                    shelf(options.scope.name)
                }
                shelfPage(1, listOf(listed))
            }
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
        controller.accept(ShelvesIntent.ShowCollection(ShelvesCollection.SHARED))
        advanceUntilIdle()
        controller.accept(ShelvesIntent.ShowCollection(ShelvesCollection.GROUP))
        advanceUntilIdle()
        controller.accept(ShelvesIntent.ShowCollection(ShelvesCollection.PERSONAL))
        controller.accept(ShelvesIntent.SelectShelf(original.id))
        advanceUntilIdle()
        val sharedBefore = controller.state.value.shared
        val groupBefore = controller.state.value.group

        controller.accept(ShelvesIntent.OpenContentsEditor)
        advanceUntilIdle()
        controller.accept(ShelvesIntent.OpenEdit)
        controller.accept(ShelvesIntent.UpdateEditName("Renamed"))
        controller.accept(ShelvesIntent.SubmitEdit)
        advanceUntilIdle()

        assertEquals("Renamed", controller.state.value.detail.detail.shelf?.name)
        assertEquals(
            "Renamed",
            controller.state.value.personal.shelves.first { it.id == original.id }.name
        )
        assertEquals(sharedBefore, controller.state.value.shared)
        assertEquals(groupBefore, controller.state.value.group)
        assertEquals(
            ShelvesDestination.ContentsEditor(original.id, ShelvesCollection.PERSONAL),
            controller.state.value.destination
        )
    }

    @Test
    fun `successful delete removes Personal Shelf and returns without touching siblings`() =
        runTest {
            val original = shelf("personal", canEdit = true)
            val capability = RecordingShelvesCapability().apply {
                listCall = { options ->
                    val listed =
                        if (options.scope.name ==
                            "PERSONAL"
                        ) {
                            original
                        } else {
                            shelf(options.scope.name)
                        }
                    shelfPage(1, listOf(listed))
                }
                detailCall = { original }
            }
            val controller = controller(capability, this)
            controller.initialize(shelvesProfile())
            advanceUntilIdle()
            controller.accept(ShelvesIntent.ShowCollection(ShelvesCollection.SHARED))
            advanceUntilIdle()
            controller.accept(ShelvesIntent.ShowCollection(ShelvesCollection.GROUP))
            advanceUntilIdle()
            controller.accept(ShelvesIntent.ShowCollection(ShelvesCollection.PERSONAL))
            controller.accept(ShelvesIntent.SelectShelf(original.id))
            advanceUntilIdle()
            val sharedBefore = controller.state.value.shared
            val groupBefore = controller.state.value.group

            controller.accept(ShelvesIntent.OpenContentsEditor)
            advanceUntilIdle()
            controller.accept(ShelvesIntent.OpenDelete)
            assertTrue(controller.state.value.delete.open)
            controller.accept(ShelvesIntent.ConfirmDelete)
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
        controller.accept(ShelvesIntent.SelectShelf(original.id))
        advanceUntilIdle()

        controller.accept(ShelvesIntent.OpenDelete)
        controller.accept(ShelvesIntent.ConfirmDelete)
        advanceUntilIdle()

        assertEquals(
            ShelvesDestination.Detail(original.id, ShelvesCollection.PERSONAL),
            controller.state.value.destination
        )
        assertEquals(ShelfManagementFailure.UNREACHABLE, controller.state.value.delete.failure)
    }

    @Test
    fun `Personal editor mutation reconciles detail and My Shelves only`() = runTest {
        val original = shelf("personal", canEdit = true)
        val updated = original.copy(itemCount = 1, previewBooks = emptyList())
        var mutated = false
        val capability = RecordingShelvesCapability().apply {
            listCall = { options ->
                val listed = if (options.scope.name ==
                    "PERSONAL"
                ) {
                    original
                } else {
                    shelf(options.scope.name)
                }
                shelfPage(1, listOf(listed))
            }
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
        controller.accept(ShelvesIntent.ShowCollection(ShelvesCollection.SHARED))
        advanceUntilIdle()
        controller.accept(ShelvesIntent.ShowCollection(ShelvesCollection.GROUP))
        advanceUntilIdle()
        controller.accept(ShelvesIntent.ShowCollection(ShelvesCollection.PERSONAL))
        controller.accept(ShelvesIntent.SelectShelf(original.id))
        advanceUntilIdle()
        val sharedBefore = controller.state.value.shared
        val groupBefore = controller.state.value.group

        controller.accept(ShelvesIntent.OpenContentsEditor)
        advanceUntilIdle()
        controller.accept(ShelvesIntent.RequestEditorRemoval("item"))
        controller.accept(ShelvesIntent.ConfirmEditorRemoval)
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
        controller.accept(ShelvesIntent.BackFromContentsEditor)
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
            controller.accept(ShelvesIntent.ShowCollection(collection))
            advanceUntilIdle()
            controller.accept(ShelvesIntent.SelectShelf("foreign"))
            advanceUntilIdle()
            controller.accept(ShelvesIntent.OpenContentsEditor)
            assertEquals(
                ShelvesDestination.Detail("foreign", collection),
                controller.state.value.destination
            )
            controller.accept(ShelvesIntent.BackFromDetail)
        }
        assertTrue(capability.editorRequests.isEmpty())
    }

    @Test
    fun `close tears down child loading`() = runTest {
        var cancelled = false
        val capability = RecordingShelvesCapability().apply {
            listCall = {
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
        }
        val controller = controller(capability, this)
        controller.initialize(shelvesProfile())
        advanceUntilIdle()

        controller.close()
        controller.close()
        advanceUntilIdle()

        assertTrue(cancelled)
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
