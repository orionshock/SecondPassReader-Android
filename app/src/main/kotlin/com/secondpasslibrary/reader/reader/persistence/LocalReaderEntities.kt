package com.secondpasslibrary.reader.reader.persistence

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

@Entity(
    tableName = "reader_sessions",
    primaryKeys = ["accountKey", "localSessionId"],
    indices = [
        Index(value = ["accountKey", "bookId"]),
        Index(value = ["accountKey", "serverSessionId"], unique = true),
        Index(value = ["accountKey", "activeProvisionalBookId"], unique = true)
    ]
)
internal data class LocalReaderSessionEntity(
    val accountKey: String,
    val localSessionId: String,
    val bookId: String,
    val serverSessionId: String?,
    val identityKind: String,
    val serverStatus: String?,
    val activeProvisionalBookId: String?,
    val sessionName: String?,
    val sessionNotes: String,
    val startedAt: String?,
    val closedAt: String?,
    val lastActivityAt: String?,
    val serverAnnotationCount: Int?,
    val createdAtEpochMillis: Long,
    val lastUsedAtEpochMillis: Long
)

@Entity(
    tableName = "reader_progress",
    primaryKeys = ["accountKey", "localSessionId"],
    foreignKeys = [
        ForeignKey(
            entity = LocalReaderSessionEntity::class,
            parentColumns = ["accountKey", "localSessionId"],
            childColumns = ["accountKey", "localSessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
internal data class LocalReaderProgressEntity(
    val accountKey: String,
    val localSessionId: String,
    val cfi: String,
    val updatedAtEpochMillis: Long,
    val provenance: String
)

@Entity(
    tableName = "reader_annotations",
    primaryKeys = ["accountKey", "localSessionId", "clientId"],
    indices = [Index(value = ["accountKey", "localSessionId", "serverAnnotationId"])],
    foreignKeys = [
        ForeignKey(
            entity = LocalReaderSessionEntity::class,
            parentColumns = ["accountKey", "localSessionId"],
            childColumns = ["accountKey", "localSessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
internal data class LocalReaderAnnotationEntity(
    val accountKey: String,
    val localSessionId: String,
    val serverAnnotationId: String?,
    val clientId: String,
    val kind: String,
    val cfi: String,
    val locationLabel: String?,
    val quote: String?,
    val prefix: String?,
    val suffix: String?,
    val note: String?,
    val color: String?,
    val updatedAt: String,
    val syncState: String
)

@Entity(
    tableName = "reader_outbox",
    primaryKeys = ["accountKey", "outboxId"],
    indices = [
        Index(value = ["accountKey", "localSessionId", "deliveryOrder", "outboxId"]),
        Index(
            value = ["accountKey", "localSessionId", "operationKind", "annotationClientId"],
            unique = true
        )
    ],
    foreignKeys = [
        ForeignKey(
            entity = LocalReaderSessionEntity::class,
            parentColumns = ["accountKey", "localSessionId"],
            childColumns = ["accountKey", "localSessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
internal data class LocalReaderOutboxEntity(
    val accountKey: String,
    val outboxId: String,
    val bookId: String,
    val localSessionId: String,
    val operationKind: String,
    val annotationClientId: String?,
    val deliveryOrder: Int,
    val cfi: String?,
    val annotationKind: String?,
    val locationLabel: String?,
    val quote: String?,
    val prefix: String?,
    val suffix: String?,
    val note: String?,
    val color: String?,
    val updatedAtEpochMillis: Long
)

@Entity(
    tableName = "reader_continuation_outcomes",
    primaryKeys = ["accountKey", "sourceLocalSessionId"],
    indices = [Index(value = ["accountKey", "continuationLocalSessionId"])],
    foreignKeys = [
        ForeignKey(
            entity = LocalReaderSessionEntity::class,
            parentColumns = ["accountKey", "localSessionId"],
            childColumns = ["accountKey", "sourceLocalSessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
internal data class LocalReaderContinuationOutcomeEntity(
    val accountKey: String,
    val sourceLocalSessionId: String,
    val continuationLocalSessionId: String?,
    val forwardedEditCount: Int,
    val droppedDeleteCount: Int,
    val createdAtEpochMillis: Long
)
