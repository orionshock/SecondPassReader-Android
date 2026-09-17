package com.secondpasslibrary.reader.reader.persistence

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import com.secondpasslibrary.reader.reader.session.ReaderClosedSessionContinuationPlan
import com.secondpasslibrary.reader.reader.session.ReaderClosedSessionContinuationPolicy
import com.secondpasslibrary.reader.reader.session.ReaderContinuationAnnotation
import com.secondpasslibrary.reader.reader.session.ReaderContinuationProgressSource
import kotlinx.coroutines.flow.Flow

@Dao
// One cohesive Room boundary exposes explicit Reader table operations.
@Suppress("TooManyFunctions")
internal abstract class LocalReaderDao {
    @Query(
        "SELECT * FROM reader_sessions WHERE accountKey = :accountKey AND bookId = :bookId " +
            "AND identityKind = 'SERVER_CONFIRMED' AND serverStatus = 'ACTIVE' " +
            "ORDER BY lastUsedAtEpochMillis DESC LIMIT 1"
    )
    abstract suspend fun activeServerSession(
        accountKey: String,
        bookId: String
    ): LocalReaderSessionEntity?

    @Query(
        "SELECT * FROM reader_sessions WHERE accountKey = :accountKey " +
            "AND activeProvisionalBookId = :bookId LIMIT 1"
    )
    abstract suspend fun activeProvisionalSession(
        accountKey: String,
        bookId: String
    ): LocalReaderSessionEntity?

    @Query(
        "SELECT * FROM reader_sessions WHERE accountKey = :accountKey " +
            "AND localSessionId = :localSessionId LIMIT 1"
    )
    abstract suspend fun session(
        accountKey: String,
        localSessionId: String
    ): LocalReaderSessionEntity?

    @Query(
        "SELECT * FROM reader_sessions WHERE accountKey = :accountKey " +
            "AND serverSessionId = :serverSessionId LIMIT 1"
    )
    abstract suspend fun sessionByServerId(
        accountKey: String,
        serverSessionId: String
    ): LocalReaderSessionEntity?

    @Upsert
    abstract suspend fun upsertSession(session: LocalReaderSessionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertSessionIfAbsent(session: LocalReaderSessionEntity): Long

    @Transaction
    open suspend fun ensureProvisionalSession(
        candidate: LocalReaderSessionEntity,
        establishment: LocalReaderOutboxEntity
    ): LocalReaderSessionEntity {
        insertSessionIfAbsent(candidate)
        val selected =
            requireNotNull(activeProvisionalSession(candidate.accountKey, candidate.bookId))
        upsertOutbox(
            establishment.copy(
                outboxId = ReaderOutboxIdentity.session(selected.localSessionId),
                localSessionId = selected.localSessionId
            )
        )
        return selected
    }

    @Query(
        "UPDATE reader_sessions SET lastUsedAtEpochMillis = :lastUsedAt " +
            "WHERE accountKey = :accountKey AND localSessionId = :localSessionId"
    )
    abstract suspend fun touchSession(accountKey: String, localSessionId: String, lastUsedAt: Long)

    @Transaction
    open suspend fun bindAuthoritativeSession(
        session: LocalReaderSessionEntity,
        serverProgressCfi: String?,
        acknowledgeEstablishment: Boolean,
        updatedAtEpochMillis: Long
    ) {
        session.serverSessionId?.let { serverSessionId ->
            val duplicate = sessionByServerId(session.accountKey, serverSessionId)
                ?.takeIf { it.localSessionId != session.localSessionId }
            if (duplicate != null) {
                require(!hasPendingWorkForSession(session.accountKey, duplicate.localSessionId)) {
                    "A duplicate server Session binding still owns pending Reader work."
                }
                deleteSession(session.accountKey, duplicate.localSessionId)
            }
        }
        val currentProgress = progress(session.accountKey, session.localSessionId)
        upsertSession(session)
        if (currentProgress?.provenance != LocalReaderWriteProvenance.LOCAL_PENDING.name) {
            if (serverProgressCfi == null) {
                deleteProgress(session.accountKey, session.localSessionId)
            } else {
                upsertProgress(
                    LocalReaderProgressEntity(
                        session.accountKey,
                        session.localSessionId,
                        serverProgressCfi,
                        updatedAtEpochMillis,
                        LocalReaderWriteProvenance.SERVER_CONFIRMED.name
                    )
                )
            }
        }
        if (acknowledgeEstablishment) {
            deleteOutbox(
                session.accountKey,
                ReaderOutboxIdentity.session(session.localSessionId)
            )
        }
    }

    @Query(
        "SELECT * FROM reader_progress WHERE accountKey = :accountKey " +
            "AND localSessionId = :localSessionId LIMIT 1"
    )
    abstract suspend fun progress(
        accountKey: String,
        localSessionId: String
    ): LocalReaderProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertProgress(progress: LocalReaderProgressEntity)

    @Transaction
    open suspend fun writeProgress(
        progress: LocalReaderProgressEntity,
        outbox: LocalReaderOutboxEntity?
    ) {
        upsertProgress(progress)
        if (outbox == null) {
            deleteOutbox(
                progress.accountKey,
                ReaderOutboxIdentity.progress(progress.localSessionId)
            )
        } else {
            upsertOutbox(outbox)
        }
    }

    @Transaction
    open suspend fun acknowledgeProgress(
        accountKey: String,
        localSessionId: String,
        cfi: String,
        locationLabel: String?,
        updatedAtEpochMillis: Long
    ) {
        val current = progress(accountKey, localSessionId)
        val currentIntent = pendingReaderIntents(accountKey, localSessionId)
            .singleOrNull { it.operationKind == ReaderOutboxOperation.PROGRESS }
        if (current?.cfi == cfi && currentIntent?.locationLabel == locationLabel) {
            upsertProgress(
                current.copy(
                    provenance = LocalReaderWriteProvenance.SERVER_CONFIRMED.name,
                    updatedAtEpochMillis = updatedAtEpochMillis
                )
            )
            deleteOutbox(accountKey, ReaderOutboxIdentity.progress(localSessionId))
        }
    }

    @Query(
        "DELETE FROM reader_progress WHERE accountKey = :accountKey " +
            "AND localSessionId = :localSessionId"
    )
    abstract suspend fun deleteProgress(accountKey: String, localSessionId: String)

    @Query(
        "SELECT * FROM reader_annotations WHERE accountKey = :accountKey " +
            "AND localSessionId = :localSessionId AND syncState != 'LOCAL_DELETED' " +
            "ORDER BY rowid"
    )
    abstract suspend fun visibleAnnotations(
        accountKey: String,
        localSessionId: String
    ): List<LocalReaderAnnotationEntity>

    @Query(
        "SELECT * FROM reader_annotations WHERE accountKey = :accountKey " +
            "AND localSessionId = :localSessionId AND syncState != 'SERVER_CONFIRMED'"
    )
    abstract suspend fun pendingAnnotations(
        accountKey: String,
        localSessionId: String
    ): List<LocalReaderAnnotationEntity>

    @Query(
        "SELECT * FROM reader_annotations WHERE accountKey = :accountKey " +
            "AND localSessionId = :localSessionId ORDER BY rowid"
    )
    abstract suspend fun allAnnotations(
        accountKey: String,
        localSessionId: String
    ): List<LocalReaderAnnotationEntity>

    @Query(
        "SELECT * FROM reader_annotations WHERE accountKey = :accountKey " +
            "AND localSessionId = :localSessionId AND clientId = :clientId LIMIT 1"
    )
    abstract suspend fun annotation(
        accountKey: String,
        localSessionId: String,
        clientId: String
    ): LocalReaderAnnotationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAnnotation(annotation: LocalReaderAnnotationEntity)

    @Transaction
    open suspend fun writeAnnotationUpsert(
        annotation: LocalReaderAnnotationEntity,
        outbox: LocalReaderOutboxEntity
    ) {
        val serverAnnotationId = annotation(
            annotation.accountKey,
            annotation.localSessionId,
            annotation.clientId
        )?.serverAnnotationId
        upsertAnnotation(annotation.copy(serverAnnotationId = serverAnnotationId))
        upsertOutbox(outbox)
    }

    @Transaction
    open suspend fun writeAnnotationDelete(
        accountKey: String,
        localSessionId: String,
        clientId: String,
        fallback: LocalReaderAnnotationEntity?,
        deleteIntent: LocalReaderOutboxEntity
    ) {
        val current = annotation(accountKey, localSessionId, clientId) ?: fallback ?: return
        upsertAnnotation(current.copy(syncState = LocalAnnotationSync.LOCAL_DELETED))
        val existedOnServer = current.serverAnnotationId != null ||
            current.syncState == LocalAnnotationSync.SERVER_CONFIRMED
        if (existedOnServer) {
            upsertOutbox(deleteIntent)
        } else {
            deleteOutbox(accountKey, ReaderOutboxIdentity.annotation(localSessionId, clientId))
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAnnotations(annotations: List<LocalReaderAnnotationEntity>)

    @Query(
        "DELETE FROM reader_annotations WHERE accountKey = :accountKey " +
            "AND localSessionId = :localSessionId"
    )
    abstract suspend fun deleteSessionAnnotations(accountKey: String, localSessionId: String)

    @Query(
        "DELETE FROM reader_outbox WHERE accountKey = :accountKey " +
            "AND localSessionId = :localSessionId AND operationKind != 'SESSION_ESTABLISHMENT'"
    )
    abstract suspend fun deleteReaderMutationOutbox(accountKey: String, localSessionId: String)

    @Upsert
    abstract suspend fun upsertContinuationOutcome(outcome: LocalReaderContinuationOutcomeEntity)

    @Query(
        "SELECT * FROM reader_continuation_outcomes WHERE accountKey = :accountKey " +
            "AND sourceLocalSessionId = :sourceLocalSessionId LIMIT 1"
    )
    abstract suspend fun continuationOutcome(
        accountKey: String,
        sourceLocalSessionId: String
    ): LocalReaderContinuationOutcomeEntity?

    @Query(
        "SELECT * FROM reader_continuation_outcomes WHERE accountKey = :accountKey " +
            "AND consumedAtEpochMillis IS NULL " +
            "AND (forwardedEditCount > 0 OR droppedDeleteCount > 0) " +
            "ORDER BY createdAtEpochMillis, sourceLocalSessionId"
    )
    abstract fun pendingContinuationOutcomes(
        accountKey: String
    ): Flow<List<LocalReaderContinuationOutcomeEntity>>

    @Query(
        "UPDATE reader_continuation_outcomes SET consumedAtEpochMillis = :consumedAtEpochMillis " +
            "WHERE accountKey = :accountKey AND sourceLocalSessionId IN (:sourceLocalSessionIds) " +
            "AND consumedAtEpochMillis IS NULL"
    )
    abstract suspend fun consumeContinuationOutcomes(
        accountKey: String,
        sourceLocalSessionIds: List<String>,
        consumedAtEpochMillis: Long
    )

    @Transaction
    open suspend fun continueClosedSession(
        closedSession: LocalReaderSessionEntity,
        serverProgressCfi: String?,
        authoritativeAnnotations: List<LocalReaderAnnotationEntity>,
        continuationCandidate: LocalReaderSessionEntity,
        now: Long
    ): ReaderClosedSessionContinuationResult {
        // Plan from the same locked snapshot we apply; a concurrent local write cannot be lost
        // between a caller-side read/decision and this cross-table commit.
        existingContinuationResult(closedSession)?.let { return it }
        val target = activeProvisionalSession(closedSession.accountKey, closedSession.bookId)
            ?: continuationCandidate
        val pending = closedSessionPendingState(closedSession, target)
        val plan = ReaderClosedSessionContinuationPolicy.plan(
            closedSession.localSessionId,
            target.localSessionId,
            pending.annotations.map {
                ReaderContinuationAnnotation(
                    it.clientId,
                    previouslyConfirmed = it.serverAnnotationId != null,
                    deleted = it.syncState == LocalAnnotationSync.LOCAL_DELETED
                )
            },
            pending.progress?.updatedAtEpochMillis,
            pending.targetProgress?.updatedAtEpochMillis
        )
        val continuation = if (plan.needsContinuation) {
            insertSessionIfAbsent(target)
            requireNotNull(activeProvisionalSession(target.accountKey, target.bookId))
        } else {
            null
        }
        restoreClosedHistory(
            closedSession,
            serverProgressCfi,
            authoritativeAnnotations,
            now
        )
        continuation?.let { forwardPending(plan, pending, it, now) }
        return recordContinuationOutcome(closedSession, continuation, plan, now)
    }

    private suspend fun existingContinuationResult(
        source: LocalReaderSessionEntity
    ): ReaderClosedSessionContinuationResult? =
        continuationOutcome(source.accountKey, source.localSessionId)?.let { outcome ->
            ReaderClosedSessionContinuationResult(
                outcome.continuationLocalSessionId?.let { session(source.accountKey, it) },
                outcome.forwardedEditCount,
                outcome.droppedDeleteCount
            )
        }

    private suspend fun closedSessionPendingState(
        source: LocalReaderSessionEntity,
        target: LocalReaderSessionEntity
    ): ClosedSessionPendingState = ClosedSessionPendingState(
        progress(source.accountKey, source.localSessionId)?.takeIf {
            it.provenance == LocalReaderWriteProvenance.LOCAL_PENDING.name
        },
        progressLocationLabel(source.accountKey, source.localSessionId),
        pendingAnnotations(source.accountKey, source.localSessionId),
        progress(target.accountKey, target.localSessionId),
        progressLocationLabel(target.accountKey, target.localSessionId)
    )

    private suspend fun progressLocationLabel(accountKey: String, localSessionId: String): String? =
        pendingReaderIntents(accountKey, localSessionId)
            .singleOrNull { it.operationKind == ReaderOutboxOperation.PROGRESS }
            ?.locationLabel

    private suspend fun restoreClosedHistory(
        source: LocalReaderSessionEntity,
        serverProgressCfi: String?,
        authoritativeAnnotations: List<LocalReaderAnnotationEntity>,
        now: Long
    ) {
        upsertSession(source)
        if (serverProgressCfi == null) {
            deleteProgress(source.accountKey, source.localSessionId)
        } else {
            upsertProgress(
                LocalReaderProgressEntity(
                    source.accountKey,
                    source.localSessionId,
                    serverProgressCfi,
                    now,
                    LocalReaderWriteProvenance.SERVER_CONFIRMED.name
                )
            )
        }
        deleteSessionAnnotations(source.accountKey, source.localSessionId)
        upsertAnnotations(authoritativeAnnotations)
        deleteReaderMutationOutbox(source.accountKey, source.localSessionId)
    }

    private suspend fun forwardPending(
        plan: ReaderClosedSessionContinuationPlan,
        pending: ClosedSessionPendingState,
        continuation: LocalReaderSessionEntity,
        now: Long
    ) {
        upsertOutbox(continuation.toEstablishmentOutbox(now))
        val selectedProgress = when (plan.progressSource) {
            ReaderContinuationProgressSource.CLOSED_SESSION ->
                requireNotNull(pending.progress).copy(localSessionId = continuation.localSessionId)

            ReaderContinuationProgressSource.CONTINUATION -> requireNotNull(pending.targetProgress)

            null -> null
        }
        selectedProgress?.let {
            val label = when (plan.progressSource) {
                ReaderContinuationProgressSource.CLOSED_SESSION -> pending.progressLocationLabel
                else -> pending.targetProgressLocationLabel
            }
            upsertProgress(it)
            upsertOutbox(it.toOutbox(continuation.bookId, label))
        }
        val annotations = pending.annotations.associateBy { it.clientId }
        plan.annotationCopies.forEach { copy ->
            val target = requireNotNull(annotations[copy.sourceClientId]).copy(
                localSessionId = continuation.localSessionId,
                serverAnnotationId = null,
                clientId = copy.targetClientId,
                syncState = LocalAnnotationSync.LOCAL_PENDING
            )
            upsertAnnotation(target)
            upsertOutbox(target.toUpsertOutbox(continuation.bookId, now))
        }
    }

    private suspend fun recordContinuationOutcome(
        source: LocalReaderSessionEntity,
        continuation: LocalReaderSessionEntity?,
        plan: ReaderClosedSessionContinuationPlan,
        now: Long
    ): ReaderClosedSessionContinuationResult {
        upsertContinuationOutcome(
            LocalReaderContinuationOutcomeEntity(
                source.accountKey,
                source.localSessionId,
                continuation?.localSessionId,
                plan.forwardedEditCount,
                plan.droppedDeleteCount,
                now,
                null
            )
        )
        return ReaderClosedSessionContinuationResult(
            continuation,
            plan.forwardedEditCount,
            plan.droppedDeleteCount
        )
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertOutbox(intent: LocalReaderOutboxEntity)

    @Query(
        "SELECT * FROM reader_outbox WHERE accountKey = :accountKey " +
            "AND localSessionId = :localSessionId ORDER BY deliveryOrder, outboxId"
    )
    abstract suspend fun pendingReaderIntents(
        accountKey: String,
        localSessionId: String
    ): List<LocalReaderOutboxEntity>

    @Query(
        "SELECT s.* FROM reader_sessions s WHERE s.accountKey = :accountKey " +
            "AND EXISTS (SELECT 1 FROM reader_outbox o WHERE o.accountKey = s.accountKey " +
            "AND o.localSessionId = s.localSessionId) " +
            "ORDER BY s.lastUsedAtEpochMillis, s.localSessionId"
    )
    abstract suspend fun pendingOutboxSessions(accountKey: String): List<LocalReaderSessionEntity>

    @Query(
        "SELECT DISTINCT s.* FROM reader_sessions s " +
            "INNER JOIN reader_outbox o ON o.accountKey = s.accountKey " +
            "AND o.localSessionId = s.localSessionId " +
            "WHERE s.accountKey = :accountKey AND s.serverSessionId IS NOT NULL " +
            "AND s.serverStatus = 'ACTIVE' " +
            "AND NOT EXISTS (SELECT 1 FROM reader_outbox establishment " +
            "WHERE establishment.accountKey = s.accountKey " +
            "AND establishment.localSessionId = s.localSessionId " +
            "AND establishment.operationKind = 'SESSION_ESTABLISHMENT') " +
            "ORDER BY s.lastUsedAtEpochMillis, s.localSessionId"
    )
    abstract suspend fun boundPendingSessions(accountKey: String): List<LocalReaderSessionEntity>

    @Transaction
    open suspend fun mergeAuthoritativeAnnotations(
        accountKey: String,
        localSessionId: String,
        acknowledgementCandidates: List<ReaderOutboxIntent>,
        authoritative: List<LocalReaderAnnotationEntity>
    ) {
        val currentIntents = pendingReaderIntents(accountKey, localSessionId)
            .associateBy(LocalReaderOutboxEntity::outboxId)
        val acknowledged = acknowledgementCandidates.filter { intent ->
            currentIntents[intent.id]?.toIntent() == intent
        }
        val acknowledgedClientIds = acknowledged.mapNotNull { intent ->
            when (intent) {
                is ReaderOutboxIntent.AnnotationDelete -> intent.clientId
                is ReaderOutboxIntent.AnnotationUpsert -> intent.clientId
                else -> null
            }
        }.toSet()
        val newerPending = pendingAnnotations(accountKey, localSessionId).filterNot {
            it.clientId in acknowledgedClientIds
        }
        deleteSessionAnnotations(accountKey, localSessionId)
        upsertAnnotations(authoritative + newerPending)
        acknowledged.forEach { deleteOutbox(accountKey, it.id) }
    }

    @Query("SELECT EXISTS(SELECT 1 FROM reader_outbox WHERE accountKey = :accountKey LIMIT 1)")
    abstract suspend fun hasPendingWork(accountKey: String): Boolean

    @Query(
        "SELECT EXISTS(SELECT 1 FROM reader_outbox WHERE accountKey = :accountKey " +
            "AND localSessionId = :localSessionId LIMIT 1)"
    )
    abstract suspend fun hasPendingWorkForSession(
        accountKey: String,
        localSessionId: String
    ): Boolean

    @Query("DELETE FROM reader_outbox WHERE accountKey = :accountKey AND outboxId = :outboxId")
    abstract suspend fun deleteOutbox(accountKey: String, outboxId: String)

    @Query(
        "DELETE FROM reader_sessions WHERE accountKey = :accountKey " +
            "AND localSessionId = :localSessionId"
    )
    abstract suspend fun deleteSession(accountKey: String, localSessionId: String)

    @Query("DELETE FROM reader_sessions WHERE accountKey = :accountKey")
    abstract suspend fun purgeAccount(accountKey: String)
}

private data class ClosedSessionPendingState(
    val progress: LocalReaderProgressEntity?,
    val progressLocationLabel: String?,
    val annotations: List<LocalReaderAnnotationEntity>,
    val targetProgress: LocalReaderProgressEntity?,
    val targetProgressLocationLabel: String?
)
