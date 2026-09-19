package com.secondpasslibrary.reader.connection

import com.secondpasslibrary.client.SplClientException

internal object ConnectionErrorPresenter {
    fun message(error: Throwable): String = storageMessage(error) ?: clientMessage(error)

    private fun storageMessage(error: Throwable): String? = when (error) {
        is ConnectionPersistenceClearException ->
            "Could not remove the connection from this device. Retry."

        is CredentialStorageException ->
            "Couldn’t save the connection securely. Retry or remove its local data."

        is ConnectionProfileStorageException ->
            "Couldn’t save the connection on this device. Retry or remove its local data."

        else -> null
    }

    private fun clientMessage(error: Throwable): String = when (error) {
        is SplClientException.InvalidServerUrl ->
            "Enter a valid Library address."

        is SplClientException.ServerUnreachable ->
            "Couldn’t reach that Library. Check the address or your connection."

        is SplClientException.NotSecondPassServer ->
            "That address isn’t a Second Pass Library. Check the address."

        is SplClientException.PairingValidationRejected ->
            "Use a device name between 1 and 200 characters."

        is SplClientException.PairingThrottled ->
            "Too many requests. Try again shortly."

        is SplClientException.AuthenticationRejected ->
            "This connection is no longer authorized. Repair the connection."

        is SplClientException.AmbiguousConsumeFailure ->
            "This approval may already have been used. Start again."

        is SplClientException ->
            "Couldn’t connect to Second Pass Library. Check the address and retry."

        else -> "Couldn’t connect. Check the address and retry."
    }
}
