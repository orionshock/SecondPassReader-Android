package com.secondpasslibrary.reader.marginalia.detail

import com.secondpasslibrary.client.ReadingSessionDetailResult
import com.secondpasslibrary.reader.marginalia.MarginaliaFailure

internal data class ReadingSessionDetailState(
    val sessionId: String? = null,
    val detail: ReadingSessionDetailResult? = null,
    val loading: Boolean = false,
    val failure: MarginaliaFailure? = null
)

internal enum class ReadingSessionNameError {
    TOO_LONG,
    SERVER_REJECTED
}

internal enum class ReadingSessionMutationFailure {
    UNREACHABLE,
    AUTHENTICATION_REJECTED,
    SESSION_CLOSED,
    NOT_AUTHORIZED,
    NOT_FOUND,
    VALIDATION,
    PROTOCOL_INVALID,
    OTHER
}

internal const val MAX_READING_SESSION_NAME_LENGTH = 255
