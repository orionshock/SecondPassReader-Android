package com.secondpasslibrary.reader.marginalia.detail.close

import com.secondpasslibrary.client.ReadingSessionFinalization
import com.secondpasslibrary.reader.marginalia.detail.ReadingSessionMutationFailure
import com.secondpasslibrary.reader.marginalia.detail.ReadingSessionNameError

internal data class ReadingSessionCloseState(
    val open: Boolean = false,
    val sessionId: String? = null,
    val name: String = "",
    val notes: String = "",
    val nameError: ReadingSessionNameError? = null,
    val closing: Boolean = false,
    val failure: ReadingSessionMutationFailure? = null,
    val retryFinalization: ReadingSessionFinalization? = null
) {
    val unnamedWarning: Boolean get() = name.isBlank()
    val exactRetryRequired: Boolean get() = retryFinalization != null
}
