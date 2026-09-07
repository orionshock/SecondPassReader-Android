package com.secondpasslibrary.reader.reader.persistence

import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.session.ReaderSessionIdentityKind
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus

internal class InvalidLocalReaderStateException(table: String, identity: String, reason: String) :
    IllegalStateException("Invalid $table row ($identity): $reason")

internal object LocalReaderPersistedStateValidator {
    fun session(entity: LocalReaderSessionEntity) {
        val identity = enumValue<ReaderSessionIdentityKind>(
            "reader_sessions",
            entity.localSessionId,
            "identityKind",
            entity.identityKind
        )
        when (identity) {
            ReaderSessionIdentityKind.PROVISIONAL -> invalidUnless(
                entity.serverSessionId == null &&
                    entity.serverStatus == null &&
                    entity.activeProvisionalBookId == entity.bookId,
                "reader_sessions",
                entity.localSessionId,
                "provisional Session has server authority or is not the active provisional Book"
            )

            ReaderSessionIdentityKind.SERVER_CONFIRMED -> {
                invalidUnless(
                    !entity.serverSessionId.isNullOrBlank() &&
                        entity.serverStatus != null &&
                        entity.activeProvisionalBookId == null,
                    "reader_sessions",
                    entity.localSessionId,
                    "server-confirmed Session lacks authority or retains provisional state"
                )
                enumValue<ReaderSessionStatus>(
                    "reader_sessions",
                    entity.localSessionId,
                    "serverStatus",
                    entity.serverStatus!!
                )
            }
        }
    }

    fun annotation(entity: LocalReaderAnnotationEntity) {
        invalidUnless(
            entity.syncState in setOf(
                LocalAnnotationSync.SERVER_CONFIRMED,
                LocalAnnotationSync.LOCAL_PENDING,
                LocalAnnotationSync.LOCAL_DELETED
            ),
            "reader_annotations",
            entity.clientId,
            "unknown syncState"
        )
        when (entity.kind) {
            LocalAnnotationKind.BOOKMARK -> invalidUnless(
                listOf(entity.quote, entity.prefix, entity.suffix, entity.note, entity.color)
                    .all { it == null },
                "reader_annotations",
                entity.clientId,
                "bookmark contains highlight body"
            )

            LocalAnnotationKind.HIGHLIGHT -> {
                invalidUnless(
                    entity.quote != null && entity.color != null,
                    "reader_annotations",
                    entity.clientId,
                    "highlight lacks quote or color"
                )
                enumValue<ReaderAnnotationColor>(
                    "reader_annotations",
                    entity.clientId,
                    "color",
                    entity.color!!
                )
            }

            else -> invalid("reader_annotations", entity.clientId, "unknown annotation kind")
        }
    }

    fun outbox(entity: LocalReaderOutboxEntity) {
        val body = listOf(
            entity.cfi,
            entity.annotationKind,
            entity.locationLabel,
            entity.quote,
            entity.prefix,
            entity.suffix,
            entity.note,
            entity.color
        )
        when (entity.operationKind) {
            ReaderOutboxOperation.SESSION_ESTABLISHMENT -> invalidUnless(
                entity.annotationClientId == null && body.all { it == null },
                "reader_outbox",
                entity.outboxId,
                "Session establishment contains mutation payload"
            )

            ReaderOutboxOperation.PROGRESS -> invalidUnless(
                entity.isValidProgressPayload(),
                "reader_outbox",
                entity.outboxId,
                "progress payload is malformed"
            )

            ReaderOutboxOperation.ANNOTATION_DELETE -> invalidUnless(
                !entity.annotationClientId.isNullOrBlank() && body.all { it == null },
                "reader_outbox",
                entity.outboxId,
                "annotation delete payload is malformed"
            )

            ReaderOutboxOperation.ANNOTATION_UPSERT -> {
                invalidUnless(
                    !entity.annotationClientId.isNullOrBlank() && !entity.cfi.isNullOrBlank() &&
                        entity.annotationKind != null,
                    "reader_outbox",
                    entity.outboxId,
                    "annotation upsert lacks identity or anchor"
                )
                annotation(
                    LocalReaderAnnotationEntity(
                        entity.accountKey, entity.localSessionId, null,
                        entity.annotationClientId!!, entity.annotationKind!!, entity.cfi!!,
                        entity.locationLabel, entity.quote, entity.prefix, entity.suffix,
                        entity.note, entity.color, "outbox", LocalAnnotationSync.LOCAL_PENDING
                    )
                )
            }

            else -> invalid("reader_outbox", entity.outboxId, "unknown operation kind")
        }
    }

    private inline fun <reified T : Enum<T>> enumValue(
        table: String,
        identity: String,
        field: String,
        value: String
    ): T = enumValues<T>().firstOrNull { it.name == value }
        ?: invalid(table, identity, "unknown $field")

    private fun invalidUnless(valid: Boolean, table: String, identity: String, reason: String) {
        if (!valid) invalid(table, identity, reason)
    }

    private fun invalid(table: String, identity: String, reason: String): Nothing =
        throw InvalidLocalReaderStateException(table, identity, reason)
}

private fun LocalReaderOutboxEntity.isValidProgressPayload(): Boolean =
    !cfi.isNullOrBlank() && annotationClientId == null && annotationKind == null &&
        quote == null && prefix == null && suffix == null && note == null && color == null
