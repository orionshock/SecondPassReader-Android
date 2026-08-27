package com.secondpasslibrary.reader.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearance
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearanceStore
import com.secondpasslibrary.reader.reader.marginalia.preferences.DEFAULT_AUTO_SHOW_PREVIOUS
import com.secondpasslibrary.reader.reader.marginalia.preferences.ReaderMarginaliaLayerPreferenceStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class ReaderSettingsState(
    val appearance: ReaderAppearance = ReaderAppearance(),
    val autoShowPreviousMarginalia: Boolean = DEFAULT_AUTO_SHOW_PREVIOUS
)

@HiltViewModel
internal class ReaderSettingsViewModel
@Inject
constructor(
    private val appearanceStore: ReaderAppearanceStore,
    private val marginaliaPreferenceStore: ReaderMarginaliaLayerPreferenceStore
) : ViewModel() {
    private val mutableState = MutableStateFlow(ReaderSettingsState())
    val state = mutableState.asStateFlow()

    private var appearanceRevision = 0L
    private var marginaliaRevision = 0L

    init {
        loadAppearance()
        loadMarginaliaPreference()
    }

    fun updateAppearance(appearance: ReaderAppearance) {
        appearanceRevision += 1
        mutableState.update { it.copy(appearance = appearance) }
        viewModelScope.launch { runCatching { appearanceStore.write(appearance) } }
    }

    fun setAutoShowPreviousMarginalia(enabled: Boolean) {
        marginaliaRevision += 1
        mutableState.update { it.copy(autoShowPreviousMarginalia = enabled) }
        viewModelScope.launch {
            runCatching { marginaliaPreferenceStore.writeAutoShowPrevious(enabled) }
        }
    }

    private fun loadAppearance() {
        val revision = appearanceRevision
        viewModelScope.launch {
            val appearance = runCatching { appearanceStore.read() }.getOrDefault(ReaderAppearance())
            if (revision == appearanceRevision) {
                mutableState.update { it.copy(appearance = appearance) }
            }
        }
    }

    private fun loadMarginaliaPreference() {
        val revision = marginaliaRevision
        viewModelScope.launch {
            val enabled = runCatching {
                marginaliaPreferenceStore.readAutoShowPrevious()
            }.getOrDefault(DEFAULT_AUTO_SHOW_PREVIOUS)
            if (revision == marginaliaRevision) {
                mutableState.update { it.copy(autoShowPreviousMarginalia = enabled) }
            }
        }
    }
}
