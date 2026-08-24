package com.secondpasslibrary.reader.reader.progress

import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import com.secondpasslibrary.reader.reader.domain.ReaderEngine
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
    val captureEnabled: Boolean,
    val latestCandidate: EpubCfi?,
    val candidateVersion: Long
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
            captureEnabled = false,
            latestCandidate = null,
            candidateVersion = 0
        )
    }

    /** Starts capture only after saved-location restoration has reached a terminal state. */
    fun enableAfterStartupRestore() {
        val capture = prepared ?: return
        if (capture.session.status != ReaderSessionStatus.ACTIVE || captureJob?.isActive == true) {
            return
        }
        mutableState.update { current -> current?.copy(captureEnabled = true) }
        captureJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            capture.engine.viewportMovements.settled().collectLatest {
                captureCurrentPosition(capture)
            }
        }
    }

    private suspend fun captureCurrentPosition(capture: PreparedCapture) {
        val cfi = capturePosition(capture.engine)
        currentCoroutineContext().ensureActive()
        if (capture.generation == generation && cfi != null) {
            publishCandidate(capture.session.sessionId, cfi)
        }
    }

    private suspend fun capturePosition(engine: ReaderEngine): EpubCfi? = try {
        (engine.cfiNavigator.currentPosition() as? EpubCfiOutcome.Success)?.value
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        null
    }

    private fun publishCandidate(sessionId: String, cfi: EpubCfi) {
        mutableState.update { current ->
            if (current == null || current.sessionId != sessionId ||
                current.latestCandidate == cfi
            ) {
                current
            } else {
                current.copy(
                    latestCandidate = cfi,
                    candidateVersion = current.candidateVersion + 1
                )
            }
        }
    }

    fun reset() {
        generation += 1
        captureJob?.cancel()
        captureJob = null
        prepared = null
        mutableState.value = null
    }

    private data class PreparedCapture(
        val session: ReaderSessionContext,
        val engine: ReaderEngine,
        val generation: Long
    )
}
