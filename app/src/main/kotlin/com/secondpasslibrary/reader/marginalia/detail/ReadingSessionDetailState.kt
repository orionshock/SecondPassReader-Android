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

internal sealed interface ReadingSessionDetailIntent {
    data object RetryDetail : ReadingSessionDetailIntent
    data object RetryAnnotations : ReadingSessionDetailIntent
    data object BeginEdit : ReadingSessionDetailIntent
    data class EditName(val value: String) : ReadingSessionDetailIntent
    data class EditNotes(val value: String) : ReadingSessionDetailIntent
    data object SaveEdit : ReadingSessionDetailIntent
    data object CancelEdit : ReadingSessionDetailIntent
    data object BeginClose : ReadingSessionDetailIntent
    data class CloseName(val value: String) : ReadingSessionDetailIntent
    data class CloseNotes(val value: String) : ReadingSessionDetailIntent
    data object ConfirmClose : ReadingSessionDetailIntent
    data object CancelClose : ReadingSessionDetailIntent
}

internal const val MAX_READING_SESSION_NAME_LENGTH = 255
