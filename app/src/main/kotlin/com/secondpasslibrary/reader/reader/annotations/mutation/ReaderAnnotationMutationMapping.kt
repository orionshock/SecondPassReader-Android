package com.secondpasslibrary.reader.reader.annotations.mutation

import com.secondpasslibrary.client.MAX_HIGHLIGHT_NOTE_LENGTH
import com.secondpasslibrary.client.ReadingSessionLifecycleRejection
import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor

internal fun ReaderPendingHighlight.updated(color: ReaderAnnotationColor?, note: String?) = copy(
    color = color ?: this.color,
    note = note.validNoteOr(this.note)
)

internal fun ReaderHighlightEditDraft.updated(color: ReaderAnnotationColor?, note: String?) = copy(
    color = color ?: this.color,
    note = note.validNoteOr(this.note)
)

private fun String?.validNoteOr(fallback: String) =
    if (this != null && length <= MAX_HIGHLIGHT_NOTE_LENGTH) this else fallback

internal fun ReaderPendingHighlight.toRequest(sessionId: String) =
    ReaderAnnotationMutationRequest.UpsertHighlight(
        sessionId = sessionId,
        clientId = clientId,
        cfi = selection.cfi.value,
        locationLabel = selection.locationLabel,
        text = selection.selectedText,
        prefix = selection.prefix,
        suffix = selection.suffix,
        color = color,
        note = note
    )

internal fun ReaderHighlightEditDraft.toRequest(sessionId: String) =
    ReaderAnnotationMutationRequest.UpsertHighlight(
        sessionId = sessionId,
        clientId = annotation.clientId,
        cfi = annotation.cfi,
        locationLabel = annotation.locationLabel,
        text = annotation.quote,
        prefix = annotation.prefix,
        suffix = annotation.suffix,
        color = color,
        note = note
    )

internal fun ReaderPendingBookmark.toRequest(sessionId: String) =
    ReaderAnnotationMutationRequest.UpsertBookmark(
        sessionId = sessionId,
        clientId = clientId,
        cfi = position.cfi.value,
        locationLabel = locationLabel
    )

internal fun validateClientId(value: String) {
    require(value.isNotBlank() && value.length <= MAX_CLIENT_ID_LENGTH) {
        "Invalid annotation client ID."
    }
}

internal fun Throwable.toMutationFailure(): ReaderAnnotationMutationFailure = when (this) {
    is SplClientException.AuthenticationRejected ->
        ReaderAnnotationMutationFailure.AUTHENTICATION_REQUIRED

    is SplClientException.ReadingSessionLifecycleRejected -> when (reason) {
        ReadingSessionLifecycleRejection.SESSION_CLOSED ->
            ReaderAnnotationMutationFailure.SESSION_CLOSED

        else -> ReaderAnnotationMutationFailure.REJECTED
    }

    else -> ReaderAnnotationMutationFailure.UNAVAILABLE
}

private const val MAX_CLIENT_ID_LENGTH = 255
