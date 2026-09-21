package com.secondpasslibrary.reader.connection

import com.secondpasslibrary.client.AuthenticatedSecondPassClient
import com.secondpasslibrary.client.AuthenticatedSecondPassClientFactory
import javax.inject.Inject

interface AuthenticatedClientProvider {
    suspend fun forProfile(profile: ConnectionProfile): AuthenticatedSecondPassClient
}

internal class StoredAuthenticatedClientProvider
@Inject
constructor(
    private val clientFactory: AuthenticatedSecondPassClientFactory,
    private val credentialStore: BearerCredentialStore,
    private val routesStore: KnownServerRoutesStore
) : AuthenticatedClientProvider {
    override suspend fun forProfile(profile: ConnectionProfile): AuthenticatedSecondPassClient {
        val stored =
            credentialStore.read()
                ?: throw CredentialStorageException("The stored credential is missing.")
        val route = routesStore.read(profile.serverId)?.activeLibraryBaseUrl
            ?: profile.libraryBaseUrl
        return clientFactory.authenticated(route, stored.credential)
    }
}
