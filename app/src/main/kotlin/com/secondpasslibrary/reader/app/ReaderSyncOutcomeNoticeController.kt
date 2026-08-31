package com.secondpasslibrary.reader.app

import com.secondpasslibrary.reader.reader.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.persistence.PendingReaderContinuationOutcome
import com.secondpasslibrary.reader.reader.persistence.ReaderContinuationOutcomeNoticeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class ReaderSyncOutcomeNotice(
    val id: Long,
    val affectedSessionCount: Int,
    val forwardedEditCount: Int,
    val droppedDeleteCount: Int
)

internal object ReaderSyncOutcomeNoticePresenter {
    fun message(notice: ReaderSyncOutcomeNotice): String {
        val edits = notice.forwardedEditCount
        val deletes = notice.droppedDeleteCount
        val prefix = if (notice.affectedSessionCount == 1) {
            "Your previous reading session closed while you were offline."
        } else {
            "While you were offline, previous reading sessions closed."
        }
        val editCopy = when (edits) {
            0 -> null
            1 -> "1 annotation edit was carried into ${notice.editDestination}."
            else -> "$edits annotation edits were carried into ${notice.editDestination}."
        }
        val deleteCopy = when (deletes) {
            0 -> null

            1 -> "1 deletion couldn’t be applied because the previous session had already closed."

            else ->
                "$deletes deletions couldn’t be applied because the previous sessions " +
                    "had already closed."
        }
        return listOfNotNull(prefix, editCopy, deleteCopy).joinToString(" ")
    }

    private val ReaderSyncOutcomeNotice.editDestination: String
        get() = if (affectedSessionCount == 1) {
            "your current session"
        } else {
            "new reading sessions"
        }
}

internal class ReaderSyncOutcomeNoticeController(
    private val store: ReaderContinuationOutcomeNoticeStore,
    private val scope: CoroutineScope
) {
    private val mutableNotice = MutableStateFlow<ReaderSyncOutcomeNotice?>(null)
    private var account: LocalReaderAccountKey? = null
    private var observation: Job? = null
    private var latestPending = emptyList<PendingReaderContinuationOutcome>()
    private var activeBatch: ActiveBatch? = null
    private var nextNoticeId = 0L

    val notice: StateFlow<ReaderSyncOutcomeNotice?> = mutableNotice.asStateFlow()

    fun update(account: LocalReaderAccountKey?) {
        if (this.account == account) return
        observation?.cancel()
        this.account = account
        latestPending = emptyList()
        activeBatch = null
        mutableNotice.value = null
        if (account == null) return
        observation = scope.launch {
            store.pendingOutcomes(account).collect { pending ->
                latestPending = pending.filter {
                    it.forwardedEditCount > 0 ||
                        it.droppedDeleteCount > 0
                }
                publishIfIdle(account)
            }
        }
    }

    fun acknowledge(noticeId: Long) {
        val batch = activeBatch?.takeIf { it.notice.id == noticeId } ?: return
        scope.launch {
            store.consume(batch.account, batch.sourceLocalSessionIds)
            if (activeBatch != batch) return@launch
            val consumed = batch.sourceLocalSessionIds.toSet()
            latestPending = latestPending.filterNot { it.sourceLocalSessionId in consumed }
            activeBatch = null
            mutableNotice.value = null
            publishIfIdle(batch.account)
        }
    }

    fun clear() {
        observation?.cancel()
        observation = null
        account = null
        latestPending = emptyList()
        activeBatch = null
        mutableNotice.value = null
    }

    private fun publishIfIdle(expectedAccount: LocalReaderAccountKey) {
        if (
            account != expectedAccount ||
            activeBatch != null ||
            latestPending.isEmpty()
        ) {
            return
        }
        val notice = ReaderSyncOutcomeNotice(
            id = ++nextNoticeId,
            affectedSessionCount = latestPending.size,
            forwardedEditCount = latestPending.sumOf { it.forwardedEditCount },
            droppedDeleteCount = latestPending.sumOf { it.droppedDeleteCount }
        )
        activeBatch = ActiveBatch(
            expectedAccount,
            latestPending.map { it.sourceLocalSessionId },
            notice
        )
        mutableNotice.value = notice
    }

    private data class ActiveBatch(
        val account: LocalReaderAccountKey,
        val sourceLocalSessionIds: List<String>,
        val notice: ReaderSyncOutcomeNotice
    )
}
