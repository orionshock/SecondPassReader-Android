package com.secondpasslibrary.reader.reader.annotations

import com.secondpasslibrary.reader.reader.cfi.EpubCfiPosition

internal data class ReaderAnnotationMutationState(
    val pendingCreate: ReaderPendingHighlight? = null,
    val pendingBookmark: ReaderPendingBookmark? = null,
    val editing: ReaderHighlightEditDraft? = null,
    val deleting: ReaderAnnotation? = null,
    val submitting: Boolean = false,
    val failure: ReaderAnnotationMutationFailure? = null
)

internal sealed interface ReaderAnnotationMutationIntent {
    data class BeginCreate(val selection: ReaderSelection) : ReaderAnnotationMutationIntent

    data class UpdateCreate(val color: ReaderAnnotationColor? = null, val note: String? = null) :
        ReaderAnnotationMutationIntent

    data object SubmitCreate : ReaderAnnotationMutationIntent
    data class BeginEdit(val annotation: ReaderAnnotation.Highlight) :
        ReaderAnnotationMutationIntent

    data class UpdateEdit(val color: ReaderAnnotationColor? = null, val note: String? = null) :
        ReaderAnnotationMutationIntent

    data object SaveEdit : ReaderAnnotationMutationIntent
    data class CreateBookmark(val position: EpubCfiPosition) : ReaderAnnotationMutationIntent
    data object RetryBookmark : ReaderAnnotationMutationIntent
    data class RequestDelete(val annotation: ReaderAnnotation) :
        ReaderAnnotationMutationIntent
    data object ConfirmDelete : ReaderAnnotationMutationIntent
    data object DismissTransient : ReaderAnnotationMutationIntent
    data object DismissCreate : ReaderAnnotationMutationIntent
}

internal data class ReaderPendingHighlight(
    val clientId: String,
    val selection: ReaderSelection,
    val color: ReaderAnnotationColor = ReaderAnnotationColor.YELLOW,
    val note: String = ""
)

internal data class ReaderPendingBookmark(
    val clientId: String,
    val position: EpubCfiPosition,
    val locationLabel: String
)

internal data class ReaderHighlightEditDraft(
    val annotation: ReaderAnnotation.Highlight,
    val color: ReaderAnnotationColor = annotation.color,
    val note: String = annotation.note.orEmpty()
) {
    val unchanged: Boolean
        get() = color == annotation.color && note == annotation.note.orEmpty()
}

internal enum class ReaderAnnotationMutationFailure {
    AUTHENTICATION_REQUIRED,
    SESSION_CLOSED,
    REJECTED,
    UNAVAILABLE
}
