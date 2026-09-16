package com.secondpasslibrary.reader.connection

import com.secondpasslibrary.client.SplClientException

internal object ConnectionErrorPresenter {
    fun message(error: Throwable): String = storageMessage(error) ?: clientMessage(error)

    private fun storageMessage(error: Throwable): String? = when (error) {
        is CredentialStorageException ->
            "Couldn’t save the connection securely. Retry or remove its local data."

        is ConnectionProfileStorageException ->
            "Couldn’t save the connection on this device. Retry or remove its local data."

        else -> null
    }

    private fun clientMessage(error: Throwable): String = when (error) {
        is SplClientException.InvalidServerUrl ->
            "Enter a valid HTTP or HTTPS address."

        is SplClientException.ServerUnreachable ->
            "Couldn’t reach Second Pass Library. Check the address and your connection."

        is SplClientException.NotSecondPassServer ->
            "That address isn’t a Second Pass Library server. Check the address."

        is SplClientException.PairingValidationRejected ->
            "Use a device name between 1 and 200 characters."

        is SplClientException.PairingThrottled ->
            "Second Pass Library is limiting requests. Try again shortly."

        is SplClientException.AuthenticationRejected ->
            "This connection is no longer authorized. Repair the connection."

        is SplClientException.AmbiguousConsumeFailure ->
            "The approval may already be used. Start linking again with a new code."

        is SplClientException ->
            "Couldn’t connect to Second Pass Library. Check the address and retry."

        else -> "Couldn’t connect. Check the address and retry."
    }
}
