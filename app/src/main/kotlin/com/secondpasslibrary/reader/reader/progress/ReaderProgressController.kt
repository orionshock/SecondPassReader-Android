package com.secondpasslibrary.reader.reader.progress

import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import com.secondpasslibrary.reader.reader.cfi.EpubCfiPosition
import com.secondpasslibrary.reader.reader.domain.ReaderEngine
import com.secondpasslibrary.reader.reader.location.ReaderSavedLocationLabelPolicy
import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class ReaderProgressState(
    val sessionId: String,
    val sessionStatus: ReaderSessionStatus,
    val captureEnabled: Boolean,
    val latestCandidate: EpubCfi?,
    val candidateVersion: Long,
    val latestLocationLabel: String? = null
)

/** Owns in-memory progress capture for the Reading Session currently rendered by Reader. */
internal class ReaderProgressController(private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow<ReaderProgressState?>(null)
    val state = mutableState.asStateFlow()
    private var prepared: PreparedCapture? = null
    private var captureJob: Job? = null
    private var generation = 0L

    fun prepare(session: ReaderSessionContext, engine: ReaderEngine) {
        captureJob?.cancel()
        generation += 1
        prepared = PreparedCapture(session, engine, generation)
        mutableState.value = ReaderProgressState(
            sessionId = session.sessionId,
            sessionStatus = session.status,
            captureEnabled = false,
            latestCandidate = null,
            candidateVersion = 0
        )
    }

    /** Starts capture only after saved-location restoration has reached a terminal state. */
    fun enableAfterStartupRestore() {
        val capture = prepared ?: return
        if (captureJob?.isActive == true) return
        val writesProgress = capture.session.status == ReaderSessionStatus.ACTIVE
        mutableState.update { current -> current?.copy(captureEnabled = writesProgress) }
        captureJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            capture.engine.viewportMovements.settled().collectLatest {
                captureCurrentPosition(capture, writesProgress)
            }
        }
    }

    private suspend fun captureCurrentPosition(capture: PreparedCapture, writesProgress: Boolean) {
        val position = capturePosition(capture.engine)
        currentCoroutineContext().ensureActive()
        if (capture.generation == generation && position != null) {
            capture.engine.positionRetention.retainPosition(position.cfi)
            if (writesProgress) publishCandidate(capture.session.sessionId, position)
        }
    }

    private suspend fun capturePosition(engine: ReaderEngine): EpubCfiPosition? = try {
        (engine.cfiNavigator.currentPositionWithContext() as? EpubCfiOutcome.Success)?.value
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        null
    }

    private fun publishCandidate(sessionId: String, position: EpubCfiPosition) {
        mutableState.update { current ->
            if (current == null || current.sessionId != sessionId ||
                current.latestCandidate == position.cfi
            ) {
                current
            } else {
                current.copy(
                    latestCandidate = position.cfi,
                    candidateVersion = current.candidateVersion + 1,
                    latestLocationLabel = ReaderSavedLocationLabelPolicy.create(
                        position.totalProgression,
                        position.sectionLabel,
                        position.chapterOrdinal
                    )
                )
            }
        }
    }

    /** Stops renderer capture while retaining the latest candidate for a local durability flush. */
    fun stopCapture() {
        generation += 1
        captureJob?.cancel()
        captureJob = null
        prepared = null
    }

    fun reset() {
        stopCapture()
        mutableState.value = null
    }

    private data class PreparedCapture(
        val session: ReaderSessionContext,
        val engine: ReaderEngine,
        val generation: Long
    )
}
