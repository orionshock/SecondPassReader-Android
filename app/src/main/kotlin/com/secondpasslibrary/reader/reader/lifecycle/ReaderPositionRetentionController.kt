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
@Suppress("TooManyFunctions") // One cohesive transient capture/restore lifecycle.
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
    private var retainedRevision = 0L
    private var currentAttachment: Long? = null
    private var detachedAttachment: Long? = null
    private var pendingCapture: Deferred<Unit>? = null
    private var restoreJob: Job? = null

    override fun completeStartupRestore(restoredPosition: EpubCfi?) {
        synchronized(lock) {
            if (closed) return
            startupComplete = true
            if (restoredPosition != null) {
                retainedPosition = restoredPosition
                retainedRevision += 1
            }
            launchRestoreIfNeeded()
        }
    }

    override fun captureBeforeNavigatorLoss() {
        captureCurrentPosition(reuseCompletedCapture = true)
    }

    fun captureAfterViewportMovement() {
        captureCurrentPosition(reuseCompletedCapture = false)
    }

    private fun captureCurrentPosition(reuseCompletedCapture: Boolean) {
        val request = createCaptureRequest(reuseCompletedCapture) ?: return
        val capture = controllerScope.async(start = CoroutineStart.UNDISPATCHED) {
            acceptCapture(request, capturePosition())
        }
        synchronized(lock) {
            if (closed || request.sequence != captureSequences.get()) {
                capture.cancel()
                return
            }
            pendingCapture = capture
        }
    }

    private fun createCaptureRequest(reuseCompletedCapture: Boolean): CaptureRequest? =
        synchronized(lock) {
            if (!reuseCompletedCapture && pendingCapture?.isCompleted == true) {
                pendingCapture = null
            }
            val captureBlocked = listOf(
                closed,
                !startupComplete,
                restoreJob?.isActive == true,
                currentAttachment == null,
                pendingCapture != null
            ).any { it }
            if (captureBlocked) return@synchronized null
            CaptureRequest(
                sequence = captureSequences.incrementAndGet(),
                attachment = requireNotNull(currentAttachment),
                retainedRevision = retainedRevision
            )
        }

    private fun acceptCapture(request: CaptureRequest, captured: EpubCfi?) {
        synchronized(lock) {
            val attachmentMatches = currentAttachment == request.attachment ||
                (currentAttachment == null && detachedAttachment == request.attachment)
            val requestIsCurrent = !closed &&
                request.sequence == captureSequences.get() &&
                request.retainedRevision == retainedRevision
            if (requestIsCurrent && attachmentMatches && captured != null) {
                retainedPosition = captured
                retainedRevision += 1
            }
        }
    }

    override fun retainPosition(position: EpubCfi) {
        val invalidatedCapture = synchronized(lock) {
            if (!closed && startupComplete && restoreJob?.isActive != true) {
                captureSequences.incrementAndGet()
                retainedPosition = position
                retainedRevision += 1
                pendingCapture.also { pendingCapture = null }
            } else {
                null
            }
        }
        invalidatedCapture?.cancel(
            CancellationException("Superseded by a newer settled retained position.")
        )
    }

    /** Called by the viewport before its concrete navigator is unbound. */
    fun navigatorDetached(attachment: Long) {
        synchronized(lock) {
            if (closed || currentAttachment != attachment) return
            recreationGeneration += 1
            recreationPending = true
            currentAttachment = null
            detachedAttachment = attachment
            restoreJob?.cancel()
            restoreJob = null
            launchRestoreIfNeeded()
        }
    }

    /** Keeps a replacement binding from invalidating the old navigator's bounded capture. */
    override suspend fun awaitPendingCapture(): EpubCfi? {
        val capture = synchronized(lock) { pendingCapture }
        if (capture == null) return synchronized(lock) { retainedPosition }
        val completed = withTimeoutOrNull(POSITION_CAPTURE_TIMEOUT_MILLIS) {
            capture.join()
            true
        } == true
        synchronized(lock) {
            if (pendingCapture === capture) {
                pendingCapture = null
                if (!completed) {
                    captureSequences.incrementAndGet()
                    capture.cancel()
                }
            }
            return retainedPosition
        }
    }

    /** Called after all capabilities have bound to the replacement navigator. */
    fun navigatorAttached(): Long = synchronized(lock) {
        check(!closed) { "Reader position retention is closed." }
        val attachment = attachmentSequences.incrementAndGet()
        currentAttachment = attachment
        detachedAttachment = null
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
    } catch (_: CancellationException) {
        null
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
            retainedRevision += 1
            recreationPending = false
            currentAttachment = null
            detachedAttachment = null
        }
        controllerJob.cancel()
    }

    private data class CaptureRequest(
        val sequence: Long,
        val attachment: Long,
        val retainedRevision: Long
    )

    private companion object {
        const val POSITION_CAPTURE_TIMEOUT_MILLIS = 1_500L
    }
}
