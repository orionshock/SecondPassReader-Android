package com.secondpasslibrary.reader.shelves

import com.secondpasslibrary.client.ShelfMutationField
import com.secondpasslibrary.client.ShelfMutationRejection
import com.secondpasslibrary.client.ShelfVisibility
import com.secondpasslibrary.client.SplClientException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditPersonalShelfControllerTest {
    @Test
    fun `draft initializes from detail and unchanged draft does not submit`() = runTest {
        val capability = RecordingShelvesCapability()
        val controller = controller(capability, this)

        controller.begin(
            shelf("one", canEdit = true).copy(
                name = "  Original  ",
                description = "  Description  ",
                visibility = ShelfVisibility.LISTED
            )
        )
        controller.submit {}

        assertEquals("Original", controller.state.value.name)
        assertEquals("Description", controller.state.value.description)
        assertEquals(ShelfVisibility.LISTED, controller.state.value.visibility)
        assertFalse(controller.state.value.changed)
        assertTrue(capability.updateRequests.isEmpty())
    }

    @Test
    fun `submit trims changed fields and preserves immutable ownership`() = runTest {
        val capability = RecordingShelvesCapability().apply {
            updateCall = { id, input ->
                shelf(id, canEdit = true).copy(
                    name = requireNotNull(input.name),
                    description = input.description,
                    visibility = requireNotNull(input.visibility)
                )
            }
        }
        val controller = controller(capability, this)
        controller.begin(shelf("one", canEdit = true))
        controller.updateName("  Renamed  ")
        controller.updateDescription("  Changed  ")
        controller.updateVisibility(ShelfVisibility.LISTED)

        var updatedName = ""
        controller.submit { updatedName = it.name }
        advanceUntilIdle()

        val request = capability.updateRequests.single()
        assertEquals("one", request.first)
        assertEquals("Renamed", request.second.name)
        assertEquals("Changed", request.second.description)
        assertEquals(ShelfVisibility.LISTED, request.second.visibility)
        assertEquals("Renamed", updatedName)
        assertEquals(EditPersonalShelfState(), controller.state.value)
    }

    @Test
    fun `blank and overlong names are rejected locally`() = runTest {
        val capability = RecordingShelvesCapability()
        val controller = controller(capability, this)
        controller.begin(shelf("one", canEdit = true))

        controller.updateName(" ")
        controller.submit {}
        assertEquals(ShelfMetadataFieldError.REQUIRED, controller.state.value.nameError)
        controller.updateName("x".repeat(256))
        controller.submit {}

        assertEquals(ShelfMetadataFieldError.TOO_LONG, controller.state.value.nameError)
        assertTrue(capability.updateRequests.isEmpty())
    }

    @Test
    fun `failed update preserves draft and maps server field`() = runTest {
        val capability = RecordingShelvesCapability().apply {
            updateCall = { _, _ ->
                throw SplClientException.ShelfMutationRejected(
                    ShelfMutationRejection.VALIDATION,
                    setOf(ShelfMutationField.NAME)
                )
            }
        }
        val controller = controller(capability, this)
        controller.begin(shelf("one", canEdit = true))
        controller.updateName("Kept draft")

        controller.submit {}
        advanceUntilIdle()

        assertEquals("Kept draft", controller.state.value.name)
        assertEquals(ShelfMetadataFieldError.SERVER_REJECTED, controller.state.value.nameError)
        assertEquals(ShelfManagementFailure.VALIDATION, controller.state.value.failure)
        assertTrue(controller.state.value.open)
    }

    @Test
    fun `cancel discards edit draft without mutation`() = runTest {
        val capability = RecordingShelvesCapability()
        val controller = controller(capability, this)
        controller.begin(shelf("one", canEdit = true))
        controller.updateName("Discarded")

        controller.reset()

        assertEquals(EditPersonalShelfState(), controller.state.value)
        assertTrue(capability.updateRequests.isEmpty())
    }

    @Test
    fun `authentication rejection stays distinct and emits connection event`() = runTest {
        val capability = RecordingShelvesCapability().apply {
            updateCall = { _, _ -> throw SplClientException.AuthenticationRejected() }
        }
        val controller = controller(capability, this)
        controller.begin(shelf("one", canEdit = true))
        controller.updateName("Changed")

        controller.submit {}
        advanceUntilIdle()

        assertEquals(ShelfManagementFailure.AUTHENTICATION_REJECTED, controller.state.value.failure)
        assertEquals(
            ShelvesConnectionEvent.AuthenticationRejected,
            controller.connectionEvents.first()
        )
    }

    private fun controller(capability: RecordingShelvesCapability, scope: CoroutineScope) =
        EditPersonalShelfController(
            ShelvesTestClientProvider(ShelvesTestClient(capability)),
            scope
        ).also { it.prepare(shelvesProfile()) }
}
