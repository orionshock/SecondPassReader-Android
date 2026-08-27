package com.secondpasslibrary.reader.reader.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.secondpasslibrary.reader.reader.ReaderState
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationState
import com.secondpasslibrary.reader.reader.annotations.selection.ReaderSelection
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayersState
import com.secondpasslibrary.reader.reader.marginalia.ui.ReaderMarginaliaDrawerState
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus

internal fun writableSelection(
    ready: ReaderState.Ready?,
    selection: ReaderSelection?,
    mutations: ReaderAnnotationMutationState
) = selection?.takeIf {
    ready?.session?.status == ReaderSessionStatus.ACTIVE &&
        mutations.pendingCreate?.selection?.cfi == it.cfi
}

@Composable
internal fun rememberMarginaliaDrawerState(
    currentSessionId: String,
    layers: ReaderMarginaliaLayersState
): ReaderMarginaliaDrawerState {
    val state = remember(currentSessionId) { ReaderMarginaliaDrawerState(currentSessionId) }
    LaunchedEffect(currentSessionId, layers.previousLayers) {
        state.reconcile(
            currentSessionId,
            layers.previousLayers.mapTo(mutableSetOf()) { it.summary.sessionId }
        )
    }
    return state
}
