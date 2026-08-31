package com.secondpasslibrary.reader.reader.persistence

import com.secondpasslibrary.reader.reader.LocalReaderAccountKey
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal data class PendingReaderContinuationOutcome(
    val sourceLocalSessionId: String,
    val forwardedEditCount: Int,
    val droppedDeleteCount: Int
)

internal typealias PendingReaderContinuationOutcomes = List<PendingReaderContinuationOutcome>

internal interface ReaderContinuationOutcomeNoticeStore {
    fun pendingOutcomes(account: LocalReaderAccountKey): Flow<PendingReaderContinuationOutcomes>

    suspend fun consume(account: LocalReaderAccountKey, sourceLocalSessionIds: List<String>)
}

internal class RoomReaderContinuationOutcomeNoticeStore @Inject constructor(
    private val dao: LocalReaderDao
) : ReaderContinuationOutcomeNoticeStore {
    override fun pendingOutcomes(
        account: LocalReaderAccountKey
    ): Flow<List<PendingReaderContinuationOutcome>> =
        dao.pendingContinuationOutcomes(account.value).map { outcomes ->
            outcomes.map { outcome ->
                PendingReaderContinuationOutcome(
                    sourceLocalSessionId = outcome.sourceLocalSessionId,
                    forwardedEditCount = outcome.forwardedEditCount,
                    droppedDeleteCount = outcome.droppedDeleteCount
                )
            }
        }

    override suspend fun consume(
        account: LocalReaderAccountKey,
        sourceLocalSessionIds: List<String>
    ) {
        if (sourceLocalSessionIds.isEmpty()) return
        dao.consumeContinuationOutcomes(
            account.value,
            sourceLocalSessionIds,
            Instant.now().toEpochMilli()
        )
    }
}
