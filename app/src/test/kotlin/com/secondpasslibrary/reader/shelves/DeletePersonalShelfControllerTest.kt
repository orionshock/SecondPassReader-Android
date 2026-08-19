package com.secondpasslibrary.reader.shelves

import com.secondpasslibrary.client.ShelfMutationRejection
import com.secondpasslibrary.client.SplClientException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeletePersonalShelfControllerTest {
    @Test
    fun `confirmation success reports deleted Shelf and clears state`() = runTest {
        val capability = RecordingShelvesCapability()
        val controller = controller(capability, this)
        controller.begin(shelf("one", canEdit = true).copy(name = "Delete me"))
        var deleted = ""

        controller.confirm { deleted = it }
        advanceUntilIdle()

        assertEquals(listOf("one"), capability.deleteRequests)
        assertEquals("one", deleted)
        assertFalse(controller.state.value.open)
    }

    @Test
    fun `failed delete preserves confirmation and is not treated as idempotent`() = runTest {
        val capability = RecordingShelvesCapability().apply {
            deleteCall = {
                throw SplClientException.ShelfMutationRejected(
                    ShelfMutationRejection.RESOURCE_NOT_FOUND
                )
            }
        }
        val controller = controller(capability, this)
        controller.begin(shelf("one", canEdit = true).copy(name = "Still shown"))
        var success = false

        controller.confirm { success = true }
        advanceUntilIdle()

        assertTrue(controller.state.value.open)
        assertEquals("Still shown", controller.state.value.shelfName)
        assertEquals(ShelfManagementFailure.NOT_FOUND, controller.state.value.failure)
        assertFalse(success)
    }

    private fun controller(capability: RecordingShelvesCapability, scope: CoroutineScope) =
        DeletePersonalShelfController(
            ShelvesTestClientProvider(ShelvesTestClient(capability)),
            scope
        ).also { it.prepare(shelvesProfile()) }
}
