package com.secondpasslibrary.reader.settings

import com.secondpasslibrary.reader.reader.appearance.ReaderAppearance
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearanceStore
import com.secondpasslibrary.reader.reader.appearance.ReaderLayoutMode
import com.secondpasslibrary.reader.reader.appearance.ReaderTheme
import com.secondpasslibrary.reader.reader.marginalia.preferences.ReaderMarginaliaLayerPreferenceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSettingsViewModelTest {
    @Test
    fun `loads and writes the existing Reader preference stores`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val appearanceStore = FakeAppearanceStore(
                ReaderAppearance(
                    theme = ReaderTheme.DARK,
                    fontScale = 1.2,
                    lineHeight = 1.6,
                    publisherStylesEnabled = true,
                    layoutMode = ReaderLayoutMode.AUTO
                )
            )
            val marginaliaStore = FakeMarginaliaPreferenceStore(false)
            val viewModel = ReaderSettingsViewModel(appearanceStore, marginaliaStore)
            advanceUntilIdle()

            assertEquals(ReaderTheme.DARK, viewModel.state.value.appearance.theme)
            assertEquals(ReaderLayoutMode.AUTO, viewModel.state.value.appearance.layoutMode)
            assertFalse(viewModel.state.value.autoShowPreviousMarginalia)

            val updated = ReaderAppearance(
                theme = ReaderTheme.LIGHT,
                fontScale = 1.3,
                lineHeight = 1.7,
                publisherStylesEnabled = false,
                layoutMode = ReaderLayoutMode.TWO_COLUMN
            )
            viewModel.updateAppearance(updated)
            viewModel.setAutoShowPreviousMarginalia(true)
            advanceUntilIdle()

            assertEquals(listOf(updated), appearanceStore.writes)
            assertEquals(listOf(true), marginaliaStore.writes)
            assertEquals(updated, viewModel.state.value.appearance)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeAppearanceStore(private val saved: ReaderAppearance) :
        ReaderAppearanceStore {
        val writes = mutableListOf<ReaderAppearance>()

        override suspend fun read() = saved

        override suspend fun write(appearance: ReaderAppearance) {
            writes += appearance
        }
    }

    private class FakeMarginaliaPreferenceStore(private val saved: Boolean) :
        ReaderMarginaliaLayerPreferenceStore {
        val writes = mutableListOf<Boolean>()

        override suspend fun readAutoShowPrevious() = saved

        override suspend fun writeAutoShowPrevious(enabled: Boolean) {
            writes += enabled
        }
    }
}
