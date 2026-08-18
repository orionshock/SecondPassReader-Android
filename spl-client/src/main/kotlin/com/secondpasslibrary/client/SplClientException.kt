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

    class ShelfMutationRejected(
        val reason: ShelfMutationRejection,
        val fields: Set<ShelfMutationField> = emptySet()
    ) : SplClientException("The shelf mutation was rejected.")
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
