package com.secondpasslibrary.reader.reader.annotations.selection

import com.secondpasslibrary.reader.reader.cfi.EpubCfiNavigator
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal interface ReaderSelectionEvents {
    fun changes(): Flow<Unit>

    suspend fun clear()
}

internal object EmptyReaderSelectionEvents : ReaderSelectionEvents {
    override fun changes(): Flow<Unit> = kotlinx.coroutines.flow.emptyFlow()

    override suspend fun clear() = Unit
}

/** Owns the current renderer-neutral selection and its live viewport attachment. */
internal class ReaderSelectionController(private val scope: CoroutineScope) {
    private val mutableSelection = MutableStateFlow<ReaderSelection?>(null)
    val selection = mutableSelection.asStateFlow()
    private var engineEvents: ReaderSelectionEvents? = null
    private var observeJob: Job? = null
    private var generation = 0L

    fun attach(events: ReaderSelectionEvents, navigator: EpubCfiNavigator) {
        if (engineEvents === events) return
        detach()
        val activeGeneration = generation
        engineEvents = events
        observeJob = scope.launch {
            events.changes().collectLatest {
                val captured = navigator.currentSelection()
                if (activeGeneration != generation) return@collectLatest
                mutableSelection.value = when (captured) {
                    is EpubCfiOutcome.Failure -> null
                    is EpubCfiOutcome.Success -> captured.value?.toReaderSelection()
                }
            }
        }
    }

    fun dismiss() {
        mutableSelection.value = null
        engineEvents?.let { events -> scope.launch { events.clear() } }
    }

    fun detach() {
        observeJob?.cancel()
        observeJob = null
        generation += 1
        engineEvents = null
        mutableSelection.value = null
    }
}
