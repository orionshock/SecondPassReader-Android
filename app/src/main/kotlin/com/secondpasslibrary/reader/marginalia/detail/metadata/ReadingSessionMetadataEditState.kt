package com.secondpasslibrary.reader.marginalia.detail.metadata

import com.secondpasslibrary.reader.marginalia.detail.ReadingSessionMutationFailure
import com.secondpasslibrary.reader.marginalia.detail.ReadingSessionNameError

internal data class ReadingSessionMetadataEditState(
    val open: Boolean = false,
    val sessionId: String? = null,
    val name: String = "",
    val notes: String = "",
    val originalName: String = "",
    val originalNotes: String = "",
    val nameError: ReadingSessionNameError? = null,
    val saving: Boolean = false,
    val failure: ReadingSessionMutationFailure? = null
) {
    val dirty: Boolean get() = name != originalName || notes != originalNotes
}
