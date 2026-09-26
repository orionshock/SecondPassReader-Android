package com.secondpasslibrary.client

sealed class SplClientException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
    class InvalidServerUrl : SplClientException("Enter a valid HTTP or HTTPS server address.")

    class ServerUnreachable(cause: Throwable? = null) :
        SplClientException("The server could not be reached.", cause)

    class NotSecondPassServer(cause: Throwable? = null) :
        SplClientException(
            "The address did not return valid Second Pass discovery information.",
            cause
        )

    class ProtocolInvalid(context: String, cause: Throwable? = null) :
        SplClientException("The server returned an invalid $context response.", cause)

    class PairingValidationRejected :
        SplClientException("The server rejected the client name or type.")

    class PairingThrottled : SplClientException("The server asked the client to slow down.")

    class PairingDenied : SplClientException("The pairing request was denied.")

    class PairingExpired : SplClientException("The pairing request expired.")

    class PairingAlreadyConsumed :
        SplClientException(
            "This pairing approval was already consumed and cannot return its token again."
        )

    class AmbiguousConsumeFailure(cause: Throwable? = null) :
        SplClientException(
            "The approval may have been consumed, but the credential response was not received safely.",
            cause
        )

    class AuthenticationRejected :
        SplClientException("The stored credential is invalid or revoked.")

    class AuthenticatedRequestFailed(cause: Throwable? = null) :
        SplClientException("Authenticated server context could not be loaded.", cause)

    class ClientSessionNotFound : SplClientException("The client session no longer exists.")

    @Deprecated("Use ClientSessionNotFound for a missing remote client session.")
    class ClientSessionRevocationRejected :
        SplClientException("The server could not revoke this client session.")

    class ClientSessionRevocationFailed :
        SplClientException("The client session revoke request failed.")

    class BookReadingSessionHistoryNotFound :
        SplClientException("The Book has no readable Reading Session history.")

    class ShelfMutationRejected(
        val reason: ShelfMutationRejection,
        val fields: Set<ShelfMutationField> = emptySet()
    ) : SplClientException("The shelf mutation was rejected.")

    class ReadingSessionLifecycleRejected(
        val reason: ReadingSessionLifecycleRejection,
        val fields: Set<ReadingSessionMutationField> = emptySet()
    ) : SplClientException("The Reading Session lifecycle operation was rejected.")
}

enum class ReadingSessionLifecycleRejection {
    SESSION_CLOSED,
    PERMISSION_DENIED,
    CURRENT_BOOK_ACCESS_REQUIRED,
    INVALID_REQUEST,
    VALIDATION,
    RESOURCE_NOT_FOUND
}

enum class ReadingSessionMutationField {
    NAME,
    NOTES,
    PROGRESS,
    LOCATION,
    LOCATION_LABEL,
    OPERATIONS,
    IDEMPOTENCY_KEY,
    GENERAL
}

enum class ShelfMutationRejection {
    DUPLICATE_BOOK,
    INVALID_MOVE,
    DIRECT_POSITION_UNAVAILABLE,
    VALIDATION,
    NOT_AUTHORIZED,
    RESOURCE_NOT_FOUND
}

enum class ShelfMutationField {
    NAME,
    DESCRIPTION,
    VISIBILITY,
    BOOK,
    POSITION,
    MOVE,
    GENERAL
}
