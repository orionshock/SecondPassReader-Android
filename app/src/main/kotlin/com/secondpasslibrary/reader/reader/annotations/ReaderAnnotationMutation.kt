package com.secondpasslibrary.reader.reader.annotations

internal data class ReaderAnnotationMutationState(
    val pendingCreate: ReaderPendingHighlight? = null,
    val editing: ReaderHighlightEditDraft? = null,
    val deleting: ReaderAnnotation.Highlight? = null,
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
    data class RequestDelete(val annotation: ReaderAnnotation.Highlight) :
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
