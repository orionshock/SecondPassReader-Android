package com.secondpasslibrary.client

/** Revokes the exact client session authenticated by the supplied bearer credential. */
interface ClientSessionRevocationClient {
    suspend fun revokeCurrentClientSession(
        libraryBaseUrl: String,
        credential: BearerCredential,
        clientSessionId: String
    )
}
