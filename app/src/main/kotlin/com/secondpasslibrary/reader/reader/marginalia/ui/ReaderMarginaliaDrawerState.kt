package com.secondpasslibrary.reader.reader.marginalia.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Reader UI state only: selection does not imply loading or viewport visibility. */
internal class ReaderMarginaliaDrawerState(currentSessionId: String) {
    var selectedLayerSessionId by mutableStateOf(currentSessionId)
        private set
    var showingLayerContent by mutableStateOf(false)
        private set
    private var currentSessionId = currentSessionId

    fun select(sessionId: String, availableSessionIds: Set<String>) {
        if (sessionId in availableSessionIds) {
            selectedLayerSessionId = sessionId
            showingLayerContent = true
        }
    }

    fun showLayerList() {
        showingLayerContent = false
    }

    fun reconcile(currentSessionId: String, previousSessionIds: Set<String>) {
        val currentChanged = this.currentSessionId != currentSessionId
        this.currentSessionId = currentSessionId
        if (currentChanged || selectedLayerSessionId !in previousSessionIds + currentSessionId) {
            selectedLayerSessionId = currentSessionId
            showingLayerContent = false
        }
    }
}
