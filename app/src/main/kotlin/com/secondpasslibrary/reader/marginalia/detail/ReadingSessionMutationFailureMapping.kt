package com.secondpasslibrary.reader.marginalia.detail

import com.secondpasslibrary.client.ReadingSessionLifecycleRejection
import com.secondpasslibrary.client.SplClientException

internal fun Throwable.toReadingSessionMutationFailure(): ReadingSessionMutationFailure =
    when (this) {
        is SplClientException.ServerUnreachable -> ReadingSessionMutationFailure.UNREACHABLE

        is SplClientException.AuthenticationRejected ->
            ReadingSessionMutationFailure.AUTHENTICATION_REJECTED

        is SplClientException.ProtocolInvalid -> ReadingSessionMutationFailure.PROTOCOL_INVALID

        is SplClientException.ReadingSessionLifecycleRejected -> reason.toAppFailure()

        else -> ReadingSessionMutationFailure.OTHER
    }

private fun ReadingSessionLifecycleRejection.toAppFailure(): ReadingSessionMutationFailure =
    when (this) {
        ReadingSessionLifecycleRejection.SESSION_CLOSED ->
            ReadingSessionMutationFailure.SESSION_CLOSED

        ReadingSessionLifecycleRejection.PERMISSION_DENIED,
        ReadingSessionLifecycleRejection.CURRENT_BOOK_ACCESS_REQUIRED ->
            ReadingSessionMutationFailure.NOT_AUTHORIZED

        ReadingSessionLifecycleRejection.RESOURCE_NOT_FOUND ->
            ReadingSessionMutationFailure.NOT_FOUND

        ReadingSessionLifecycleRejection.INVALID_REQUEST,
        ReadingSessionLifecycleRejection.VALIDATION -> ReadingSessionMutationFailure.VALIDATION
    }
