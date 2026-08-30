package com.secondpasslibrary.reader.reader.annotations.mutation

import com.secondpasslibrary.client.MAX_HIGHLIGHT_NOTE_LENGTH
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.annotations.ReaderQuoteContextPolicy

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

internal fun ReaderPendingHighlight.toRequest(
    sessionId: String
): ReaderAnnotationMutationRequest.UpsertHighlight? {
    val quote = ReaderQuoteContextPolicy.prepare(
        selection.selectedText,
        selection.prefix,
        selection.suffix
    ) ?: return null
    return ReaderAnnotationMutationRequest.UpsertHighlight(
        sessionId = sessionId,
        clientId = clientId,
        cfi = selection.cfi.value,
        locationLabel = selection.locationLabel,
        text = quote.exact,
        prefix = quote.prefix,
        suffix = quote.suffix,
        color = color,
        note = note
    )
}

internal fun ReaderHighlightEditDraft.toRequest(
    sessionId: String
): ReaderAnnotationMutationRequest.UpsertHighlight? {
    val quote = ReaderQuoteContextPolicy.prepare(
        annotation.quote,
        annotation.prefix,
        annotation.suffix
    ) ?: return null
    return ReaderAnnotationMutationRequest.UpsertHighlight(
        sessionId = sessionId,
        clientId = annotation.clientId,
        cfi = annotation.cfi,
        locationLabel = annotation.locationLabel,
        text = quote.exact,
        prefix = quote.prefix,
        suffix = quote.suffix,
        color = color,
        note = note
    )
}

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

private const val MAX_CLIENT_ID_LENGTH = 255
