package com.secondpasslibrary.reader.shelves

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShelvesViewModelTest {
    @Test
    fun `delegates feature intents through the Shelves parent`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val capability = RecordingShelvesCapability()
        val viewModel =
            ShelvesViewModel(ShelvesTestClientProvider(ShelvesTestClient(capability)))
        try {
            viewModel.initialize(shelvesProfile())
            advanceUntilIdle()

            viewModel.accept(ShelvesIntent.ShowCollection(ShelvesCollection.SHARED))
            advanceUntilIdle()

            assertEquals(
                ShelvesDestination.Collection(ShelvesCollection.SHARED),
                viewModel.state.value.destination
            )
            assertEquals(2, capability.listRequests.size)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }
}
