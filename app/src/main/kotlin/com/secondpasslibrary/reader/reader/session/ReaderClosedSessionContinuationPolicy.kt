package com.secondpasslibrary.reader.reader.session

import java.nio.charset.StandardCharsets
import java.util.UUID

/** Pending authored intent, independent of Room rows and annotation payload storage. */
internal data class ReaderContinuationAnnotation(
    val clientId: String,
    val previouslyConfirmed: Boolean,
    val deleted: Boolean
)

internal data class ReaderContinuationAnnotationCopy(
    val sourceClientId: String,
    val targetClientId: String,
    val replacesConfirmedAnnotation: Boolean
)

internal enum class ReaderContinuationProgressSource { CLOSED_SESSION, CONTINUATION }

internal data class ReaderClosedSessionContinuationPlan(
    val progressSource: ReaderContinuationProgressSource?,
    val annotationCopies: List<ReaderContinuationAnnotationCopy>,
    val droppedDeleteCount: Int
) {
    val needsContinuation: Boolean
        get() = progressSource != null || annotationCopies.isNotEmpty()

    val forwardedEditCount: Int
        get() = annotationCopies.count { it.replacesConfirmedAnnotation }
}

/**
 * Closed history is immutable. Pending new annotations move with their identity; confirmed edits
 * become new authored copies, and confirmed deletes are dropped. Progress uses the newest local
 * timestamp (the source wins ties), never CFI ordering.
 *
 * Persistence evaluates this policy against its locked snapshot and applies the whole plan
 * atomically, including restoration of authoritative history and durable outcome accounting.
 */
internal object ReaderClosedSessionContinuationPolicy {
    fun plan(
        sourceSessionId: String,
        continuationSessionId: String,
        annotations: List<ReaderContinuationAnnotation>,
        pendingProgressUpdatedAt: Long?,
        continuationProgressUpdatedAt: Long?
    ): ReaderClosedSessionContinuationPlan {
        val copies = annotations.filterNot { it.deleted }.map { annotation ->
            ReaderContinuationAnnotationCopy(
                sourceClientId = annotation.clientId,
                targetClientId = if (annotation.previouslyConfirmed) {
                    forwardedEditIdentity(
                        sourceSessionId,
                        continuationSessionId,
                        annotation.clientId
                    )
                } else {
                    annotation.clientId
                },
                replacesConfirmedAnnotation = annotation.previouslyConfirmed
            )
        }
        val progressSource = when {
            pendingProgressUpdatedAt == null -> null

            continuationProgressUpdatedAt == null ||
                pendingProgressUpdatedAt >= continuationProgressUpdatedAt ->
                ReaderContinuationProgressSource.CLOSED_SESSION

            else -> ReaderContinuationProgressSource.CONTINUATION
        }
        return ReaderClosedSessionContinuationPlan(
            progressSource,
            copies,
            annotations.count { it.deleted && it.previouslyConfirmed }
        )
    }

    private fun forwardedEditIdentity(
        sourceSessionId: String,
        continuationSessionId: String,
        sourceClientId: String
    ): String = UUID.nameUUIDFromBytes(
        (
            "reader-continuation-edit\u0000$sourceSessionId\u0000" +
                "$continuationSessionId\u0000$sourceClientId"
            ).toByteArray(StandardCharsets.UTF_8)
    ).toString()
}
