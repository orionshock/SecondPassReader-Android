package com.secondpasslibrary.reader.reader.persistence

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert

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
        updatedAtEpochMillis: Long
    ) {
        val current = progress(accountKey, localSessionId)
        if (current?.cfi == cfi) {
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

    @Transaction
    open suspend fun replaceAnnotations(
        accountKey: String,
        localSessionId: String,
        annotations: List<LocalReaderAnnotationEntity>,
        acknowledgedOutboxId: String? = null
    ) {
        deleteSessionAnnotations(accountKey, localSessionId)
        upsertAnnotations(annotations)
        acknowledgedOutboxId?.let { deleteOutbox(accountKey, it) }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertOutbox(intent: LocalReaderOutboxEntity)

    @Query(
        "SELECT * FROM reader_outbox WHERE accountKey = :accountKey " +
            "AND operationKind = 'SESSION_ESTABLISHMENT' " +
            "ORDER BY deliveryOrder, outboxId"
    )
    abstract suspend fun pendingSessionEstablishments(
        accountKey: String
    ): List<LocalReaderOutboxEntity>

    @Query(
        "SELECT * FROM reader_outbox WHERE accountKey = :accountKey " +
            "AND localSessionId = :localSessionId ORDER BY deliveryOrder, outboxId"
    )
    abstract suspend fun pendingReaderIntents(
        accountKey: String,
        localSessionId: String
    ): List<LocalReaderOutboxEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM reader_outbox WHERE accountKey = :accountKey LIMIT 1)")
    abstract suspend fun hasPendingWork(accountKey: String): Boolean

    @Query("DELETE FROM reader_outbox WHERE accountKey = :accountKey AND outboxId = :outboxId")
    abstract suspend fun deleteOutbox(accountKey: String, outboxId: String)

    @Query("DELETE FROM reader_sessions WHERE accountKey = :accountKey")
    abstract suspend fun purgeAccount(accountKey: String)
}
