package com.secondpasslibrary.reader.reader.lifecycle

import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.cfi.EpubCfiNavigator
import com.secondpasslibrary.reader.reader.cfi.EpubCfiOutcome
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Retains one canonical position across navigator attachments owned by the same Reader engine. */
internal class ReaderPositionRetentionController(
    private val navigator: EpubCfiNavigator,
    scope: CoroutineScope,
    private val suppressMovementCapture: suspend (suspend () -> Unit) -> Unit
) : ReaderPositionRetention,
    AutoCloseable {
    private val controllerJob = SupervisorJob(scope.coroutineContext[Job])
    private val controllerScope = CoroutineScope(scope.coroutineContext + controllerJob)
    private val lock = Any()
    private val captureSequences = AtomicLong()
    private val attachmentSequences = AtomicLong()
    private var startupComplete = false
    private var closed = false
    private var recreationGeneration = 0L
    private var recreationPending = false
    private var retainedPosition: EpubCfi? = null
    private var currentAttachment: Long? = null
    private var pendingCapture: Deferred<Unit>? = null
    private var restoreJob: Job? = null

    override fun completeStartupRestore(restoredPosition: EpubCfi?) {
        synchronized(lock) {
            if (closed) return
            startupComplete = true
            if (restoredPosition != null) retainedPosition = restoredPosition
            launchRestoreIfNeeded()
        }
    }

    override fun captureBeforeNavigatorLoss() {
        val sequence = synchronized(lock) {
            val captureBlocked = listOf(
                closed,
                !startupComplete,
                retainedPosition != null,
                restoreJob?.isActive == true
            ).any { it }
            if (captureBlocked) return
            captureSequences.incrementAndGet()
        }
        val capture = controllerScope.async(start = CoroutineStart.UNDISPATCHED) {
            val captured = capturePosition()
            synchronized(lock) {
                if (!closed && sequence == captureSequences.get() && captured != null) {
                    retainedPosition = captured
                }
            }
        }
        synchronized(lock) {
            if (closed || sequence != captureSequences.get()) {
                capture.cancel()
                return
            }
            pendingCapture = capture
        }
    }

    override fun retainPosition(position: EpubCfi) {
        synchronized(lock) {
            if (!closed && startupComplete && restoreJob?.isActive != true) {
                retainedPosition = position
            }
        }
    }

    /** Called by the viewport before its concrete navigator is unbound. */
    fun navigatorDetached(attachment: Long) {
        synchronized(lock) {
            if (closed) return
            recreationGeneration += 1
            recreationPending = true
            if (currentAttachment == attachment) currentAttachment = null
            restoreJob?.cancel()
            restoreJob = null
            launchRestoreIfNeeded()
        }
    }

    /** Keeps a replacement binding from invalidating the old navigator's bounded capture. */
    suspend fun awaitPendingCapture() {
        val capture = synchronized(lock) { pendingCapture }
        if (capture == null) return
        val completed = withTimeoutOrNull(POSITION_CAPTURE_TIMEOUT_MILLIS) {
            capture.join()
            true
        } == true
        if (!completed) {
            synchronized(lock) {
                if (pendingCapture === capture) {
                    captureSequences.incrementAndGet()
                    pendingCapture = null
                    capture.cancel()
                }
            }
        }
    }

    /** Called after all capabilities have bound to the replacement navigator. */
    fun navigatorAttached(): Long = synchronized(lock) {
        check(!closed) { "Reader position retention is closed." }
        val attachment = attachmentSequences.incrementAndGet()
        currentAttachment = attachment
        launchRestoreIfNeeded()
        attachment
    }

    private fun launchRestoreIfNeeded() {
        val restoreNeeded = listOf(
            startupComplete,
            recreationPending,
            currentAttachment != null,
            restoreJob?.isActive != true
        ).all { it }
        if (!restoreNeeded) return
        val generation = recreationGeneration
        val capture = pendingCapture
        restoreJob = controllerScope.launch {
            restoreGeneration(generation, capture)
        }
    }

    private suspend fun restoreGeneration(generation: Long, capture: Deferred<Unit>?) {
        capture?.join()
        val position = synchronized(lock) {
            retainedPosition.takeIf {
                !closed && recreationPending && generation == recreationGeneration
            }
        } ?: return
        var restored = false
        try {
            suppressMovementCapture {
                val available = navigator.awaitNavigationAvailable()
                if (available is EpubCfiOutcome.Success) {
                    restored = navigator.goTo(position) is EpubCfiOutcome.Success
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Recreation retention is best effort; the live Reader remains usable.
        }
        synchronized(lock) {
            if (!closed && restored && generation == recreationGeneration) {
                recreationPending = false
            }
        }
    }

    private suspend fun capturePosition(): EpubCfi? = try {
        withTimeoutOrNull(POSITION_CAPTURE_TIMEOUT_MILLIS) {
            (navigator.currentPosition() as? EpubCfiOutcome.Success)?.value
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            captureSequences.incrementAndGet()
            pendingCapture?.cancel()
            pendingCapture = null
            restoreJob?.cancel()
            restoreJob = null
            retainedPosition = null
            recreationPending = false
            currentAttachment = null
        }
        controllerJob.cancel()
    }

    private companion object {
        const val POSITION_CAPTURE_TIMEOUT_MILLIS = 1_500L
    }
}
