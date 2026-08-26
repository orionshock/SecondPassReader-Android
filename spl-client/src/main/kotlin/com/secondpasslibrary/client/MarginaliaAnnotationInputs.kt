package com.secondpasslibrary.client

data class MarginaliaAnnotationLocationInput(val cfi: String, val locationLabel: String? = null) {
    init {
        require(cfi.isNotBlank()) { "Annotation CFI must not be blank." }
        require(cfi.length <= MAX_CFI_LENGTH) {
            "Annotation CFI must be at most $MAX_CFI_LENGTH characters."
        }
        require(locationLabel == null || locationLabel.length <= MAX_LOCATION_LABEL_LENGTH) {
            "Annotation location label must be at most $MAX_LOCATION_LABEL_LENGTH characters."
        }
    }
}

sealed interface MarginaliaAnnotationDraft {
    val clientId: String
    val location: MarginaliaAnnotationLocationInput

    data class Bookmark(
        override val clientId: String,
        override val location: MarginaliaAnnotationLocationInput
    ) : MarginaliaAnnotationDraft {
        init {
            validateAnnotationClientId(clientId)
        }
    }

    data class Highlight(
        override val clientId: String,
        override val location: MarginaliaAnnotationLocationInput,
        val body: MarginaliaHighlightBodyInput
    ) : MarginaliaAnnotationDraft {
        init {
            validateAnnotationClientId(clientId)
        }
    }
}

data class MarginaliaHighlightBodyInput(
    val text: String,
    val prefix: String? = null,
    val suffix: String? = null,
    val color: MarginaliaHighlightColor,
    val note: String? = null
) {
    init {
        require(text.isNotBlank()) { "Highlight text must not be blank." }
        require(text.length <= MAX_HIGHLIGHT_TEXT_LENGTH) {
            "Highlight text must be at most $MAX_HIGHLIGHT_TEXT_LENGTH characters."
        }
        require(prefix == null || prefix.length <= MAX_HIGHLIGHT_CONTEXT_LENGTH) {
            "Highlight prefix must be at most $MAX_HIGHLIGHT_CONTEXT_LENGTH characters."
        }
        require(suffix == null || suffix.length <= MAX_HIGHLIGHT_CONTEXT_LENGTH) {
            "Highlight suffix must be at most $MAX_HIGHLIGHT_CONTEXT_LENGTH characters."
        }
        require(note == null || note.length <= MAX_HIGHLIGHT_NOTE_LENGTH) {
            "Highlight note must be at most $MAX_HIGHLIGHT_NOTE_LENGTH characters."
        }
    }
}

sealed interface MarginaliaAnnotationOperation {
    val clientId: String

    data class Upsert(val annotation: MarginaliaAnnotationDraft) : MarginaliaAnnotationOperation {
        override val clientId: String = annotation.clientId
    }

    data class Delete(override val clientId: String) : MarginaliaAnnotationOperation {
        init {
            validateAnnotationClientId(clientId)
        }
    }
}

internal fun validateAnnotationOperations(operations: List<MarginaliaAnnotationOperation>) {
    require(operations.size in 1..MAX_ANNOTATION_BATCH_SIZE) {
        "Annotation synchronization requires 1-$MAX_ANNOTATION_BATCH_SIZE operations."
    }
    require(operations.map { it.clientId }.toSet().size == operations.size) {
        "Each annotation client ID may appear only once in a synchronization batch."
    }
}

internal fun validateAnnotationClientId(clientId: String) {
    require(clientId.isNotBlank()) { "Annotation client ID must not be blank." }
    require(clientId.length <= MAX_ANNOTATION_CLIENT_ID_LENGTH) {
        "Annotation client ID must be at most $MAX_ANNOTATION_CLIENT_ID_LENGTH characters."
    }
}

internal const val MAX_ANNOTATION_CLIENT_ID_LENGTH = 255
internal const val MAX_HIGHLIGHT_TEXT_LENGTH = 64 * 1024
const val MAX_HIGHLIGHT_NOTE_LENGTH = 64 * 1024
internal const val MAX_HIGHLIGHT_CONTEXT_LENGTH = 500
private const val MAX_ANNOTATION_BATCH_SIZE = 100
