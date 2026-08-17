package com.secondpasslibrary.reader.connection

import com.secondpasslibrary.client.SplClientException

internal object ConnectionErrorPresenter {
    fun message(error: Throwable): String = storageMessage(error) ?: clientMessage(error)

    private fun storageMessage(error: Throwable): String? = when (error) {
        is CredentialStorageException -> error.message ?: "Secure credential storage failed."

        is ConnectionProfileStorageException ->
            error.message
                ?: "Connection profile storage failed."

        else -> null
    }

    private fun clientMessage(error: Throwable): String = when (error) {
        is SplClientException.InvalidServerUrl -> error.message.orEmpty()

        is SplClientException.ServerUnreachable ->
            "The server could not be reached. Check the address and connection."

        is SplClientException.NotSecondPassServer ->
            "That address is not advertising a valid Second Pass Library server."

        is SplClientException.PairingValidationRejected ->
            "The server rejected the client name. Use 1–200 characters."

        is SplClientException.PairingThrottled -> "The server asked this client to slow down."

        is SplClientException.AuthenticationRejected ->
            "The saved credential was rejected or revoked."

        is SplClientException.AmbiguousConsumeFailure ->
            "Approval may have been consumed, but its one-time credential was not received. " +
                "Do not retry this approval; start a new pairing request."

        is SplClientException -> error.message ?: "The Second Pass request failed."

        else -> "An unexpected connection error occurred."
    }
}
