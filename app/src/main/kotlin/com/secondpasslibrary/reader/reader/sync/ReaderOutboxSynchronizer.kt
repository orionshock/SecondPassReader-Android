package com.secondpasslibrary.reader.reader.sync

import com.secondpasslibrary.client.ReadingSessionLifecycleRejection
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationBatchWriter
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationRequest
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.persistence.LocalAnnotationKind
import com.secondpasslibrary.reader.reader.persistence.ReaderBoundOutboxSession
import com.secondpasslibrary.reader.reader.persistence.ReaderOutboxIntent
import com.secondpasslibrary.reader.reader.persistence.ReaderOutboxStore
import com.secondpasslibrary.reader.reader.progress.ReaderProgressSyncFailure
import com.secondpasslibrary.reader.reader.progress.ReaderProgressWriteOutcome
import com.secondpasslibrary.reader.reader.progress.ReaderProgressWriter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class ReaderOutboxSyncReport(
    val deliveredAnnotationIntents: Int = 0,
    val deliveredProgressIntents: Int = 0,
    val authenticationRequired: Boolean = false,
    val reconciliationSessionIds: Set<String> = emptySet(),
    val unavailable: Boolean = false
)

/** Drains coalesced Reader intents only after Session reconciliation established authority. */
@Singleton
internal class ReaderOutboxSynchronizer @Inject constructor(
    private val outbox: ReaderOutboxStore,
    private val annotations: ReaderAnnotationBatchWriter,
    private val progress: ReaderProgressWriter
) {
    private val syncMutex = Mutex()

    suspend fun syncBoundSession(
        profile: ConnectionProfile,
        account: LocalReaderAccountKey,
        localSessionId: String
    ): ReaderOutboxSyncReport = syncMutex.withLock {
        outbox.boundPendingSessions(account)
            .firstOrNull { it.localSessionId == localSessionId }
            ?.let { syncSession(profile, account, it) }
            ?: ReaderOutboxSyncReport()
    }

    private suspend fun syncSession(
        profile: ConnectionProfile,
        account: LocalReaderAccountKey,
        session: ReaderBoundOutboxSession
    ): ReaderOutboxSyncReport {
        val initial = outbox.pendingReaderIntents(account, session.localSessionId)
        return if (initial.any { it is ReaderOutboxIntent.EstablishSession }) {
            ReaderOutboxSyncReport()
        } else {
            var report = ReaderOutboxSyncReport()
            val annotationIntents = initial.filter {
                it is ReaderOutboxIntent.AnnotationUpsert ||
                    it is ReaderOutboxIntent.AnnotationDelete
            }
            for (chunk in annotationIntents.chunked(MAX_ANNOTATION_BATCH_SIZE)) {
                val outcome = deliverAnnotationChunk(profile, account, session, chunk)
                report += outcome
                if (outcome.shouldStop) break
            }
            val pendingProgress = outbox.pendingReaderIntents(account, session.localSessionId)
                .filterIsInstance<ReaderOutboxIntent.Progress>()
                .singleOrNull()
            if (report.shouldStop || pendingProgress == null) {
                report
            } else {
                report + deliverProgress(profile, account, session, pendingProgress)
            }
        }
    }

    private suspend fun deliverAnnotationChunk(
        profile: ConnectionProfile,
        account: LocalReaderAccountKey,
        session: ReaderBoundOutboxSession,
        sent: List<ReaderOutboxIntent>
    ): ReaderOutboxSyncReport = try {
        val authoritative = annotations.synchronize(
            profile,
            session.serverSessionId,
            sent.map { it.toMutation(session.serverSessionId) }
        )
        outbox.acceptAnnotationBatch(account, session.localSessionId, sent, authoritative)
        ReaderOutboxSyncReport(deliveredAnnotationIntents = sent.size)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: SplClientException.AuthenticationRejected) {
        ReaderOutboxSyncReport(authenticationRequired = true)
    } catch (failure: SplClientException.ReadingSessionLifecycleRejected) {
        if (failure.reason.requiresSessionReconciliation) {
            ReaderOutboxSyncReport(
                reconciliationSessionIds = setOf(session.localSessionId)
            )
        } else {
            ReaderOutboxSyncReport(unavailable = true)
        }
    } catch (_: Exception) {
        ReaderOutboxSyncReport(unavailable = true)
    }

    private suspend fun deliverProgress(
        profile: ConnectionProfile,
        account: LocalReaderAccountKey,
        session: ReaderBoundOutboxSession,
        sent: ReaderOutboxIntent.Progress
    ): ReaderOutboxSyncReport = when (
        val outcome = progress.replace(
            profile,
            session.serverSessionId,
            EpubCfi(sent.cfi),
            sent.locationLabel
        )
    ) {
        ReaderProgressWriteOutcome.Success -> {
            outbox.acceptProgress(account, session.localSessionId, sent)
            ReaderOutboxSyncReport(deliveredProgressIntents = 1)
        }

        is ReaderProgressWriteOutcome.Failure -> when (outcome.reason) {
            ReaderProgressSyncFailure.AUTHENTICATION_REQUIRED ->
                ReaderOutboxSyncReport(authenticationRequired = true)

            ReaderProgressSyncFailure.SESSION_NOT_WRITABLE -> ReaderOutboxSyncReport(
                reconciliationSessionIds = setOf(session.localSessionId)
            )

            ReaderProgressSyncFailure.UNAVAILABLE -> ReaderOutboxSyncReport(unavailable = true)
        }
    }

    private companion object {
        const val MAX_ANNOTATION_BATCH_SIZE = 100
    }
}

private val ReaderOutboxSyncReport.shouldStop: Boolean
    get() = authenticationRequired || unavailable || reconciliationSessionIds.isNotEmpty()

private operator fun ReaderOutboxSyncReport.plus(other: ReaderOutboxSyncReport) =
    ReaderOutboxSyncReport(
        deliveredAnnotationIntents + other.deliveredAnnotationIntents,
        deliveredProgressIntents + other.deliveredProgressIntents,
        authenticationRequired || other.authenticationRequired,
        reconciliationSessionIds + other.reconciliationSessionIds,
        unavailable || other.unavailable
    )

private val ReadingSessionLifecycleRejection.requiresSessionReconciliation: Boolean
    get() = this == ReadingSessionLifecycleRejection.SESSION_CLOSED ||
        this == ReadingSessionLifecycleRejection.RESOURCE_NOT_FOUND

private fun ReaderOutboxIntent.toMutation(serverSessionId: String) = when (this) {
    is ReaderOutboxIntent.AnnotationDelete -> ReaderAnnotationMutationRequest.Delete(
        serverSessionId,
        clientId
    )

    is ReaderOutboxIntent.AnnotationUpsert -> when (kind) {
        LocalAnnotationKind.BOOKMARK -> ReaderAnnotationMutationRequest.UpsertBookmark(
            serverSessionId,
            clientId,
            cfi,
            requireNotNull(locationLabel)
        )

        else -> ReaderAnnotationMutationRequest.UpsertHighlight(
            serverSessionId,
            clientId,
            cfi,
            locationLabel,
            requireNotNull(quote),
            prefix,
            suffix,
            requireNotNull(color),
            note.orEmpty()
        )
    }

    else -> error("Only annotation intents can enter an annotation batch.")
}
