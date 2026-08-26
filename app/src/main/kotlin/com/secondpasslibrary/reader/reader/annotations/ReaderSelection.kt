package com.secondpasslibrary.reader.reader.annotations

import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiNavigator
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import com.secondpasslibrary.reader.reader.cfi.EpubCfiSelection
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal data class ReaderSelection(
    val cfi: EpubCfi,
    val selectedText: String,
    val prefix: String?,
    val suffix: String?,
    val locationLabel: String
)

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

internal fun EpubCfiSelection.toReaderSelection(): ReaderSelection? {
    if (selectedText.isBlank()) return null
    return ReaderSelection(
        cfi = cfi,
        selectedText = selectedText,
        prefix = prefix,
        suffix = suffix,
        locationLabel = readerLocationLabel(chapterOrdinal, totalProgression)
    )
}

internal fun readerLocationLabel(chapterOrdinal: Int, totalProgression: Double?): String {
    require(chapterOrdinal > 0) { "Chapter ordinal must be positive." }
    val chapter = chapterOrdinal.toString().padStart(2, '0')
    val percentage = totalProgression
        ?.coerceIn(0.0, 1.0)
        ?.times(100)
        ?.roundToInt()
    return if (percentage == null) "Chapter $chapter" else "Chapter $chapter · $percentage%"
}
