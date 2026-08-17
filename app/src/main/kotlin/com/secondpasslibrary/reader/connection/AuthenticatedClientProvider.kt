package com.secondpasslibrary.reader.connection

import com.secondpasslibrary.client.AuthenticatedSecondPassClient
import com.secondpasslibrary.client.AuthenticatedSecondPassClientFactory
import javax.inject.Inject

interface AuthenticatedClientProvider {
    suspend fun forProfile(profile: ConnectionProfile): AuthenticatedSecondPassClient
}

class StoredAuthenticatedClientProvider
@Inject
constructor(
    private val clientFactory: AuthenticatedSecondPassClientFactory,
    private val credentialStore: BearerCredentialStore
) : AuthenticatedClientProvider {
    override suspend fun forProfile(profile: ConnectionProfile): AuthenticatedSecondPassClient {
        val stored =
            credentialStore.read()
                ?: throw CredentialStorageException("The stored credential is missing.")
        return clientFactory.authenticated(profile.apiBaseUrl, stored.credential)
    }
}
