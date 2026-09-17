package com.secondpasslibrary.reader.marginalia

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
class MarginaliaViewModelTest {
    @Test
    fun `delegates feature intents through the Marginalia parent`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val capability = RecordingMarginaliaCapability()
        val viewModel = MarginaliaViewModel(marginaliaProvider(capability))
        try {
            viewModel.initialize(marginaliaProfile())
            advanceUntilIdle()

            viewModel.accept(MarginaliaIntent.SelectBrowseMode(MarginaliaBrowseMode.BOOKS))
            advanceUntilIdle()

            assertEquals(MarginaliaBrowseMode.BOOKS, viewModel.state.value.browseMode)
            assertEquals(1, capability.marginaliaBookRequests.size)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }
}
