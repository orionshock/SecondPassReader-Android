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
        annotations: List<LocalReaderAnnotationEntity>
    ) {
        deleteSessionAnnotations(accountKey, localSessionId)
        upsertAnnotations(annotations)
    }

    @Query("DELETE FROM reader_sessions WHERE accountKey = :accountKey")
    abstract suspend fun purgeAccount(accountKey: String)
}
