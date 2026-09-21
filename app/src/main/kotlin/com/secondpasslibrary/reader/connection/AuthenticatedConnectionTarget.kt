package com.secondpasslibrary.reader.connection

import com.secondpasslibrary.client.AuthenticatedContext
import com.secondpasslibrary.client.BearerCredential
import com.secondpasslibrary.client.SecondPassClient
import com.secondpasslibrary.client.SplClientException

/** Connection-owned verification seam; endpoint selection stays behind this interface. */
internal fun interface AuthenticatedConnectionTarget {
    suspend fun loadContext(
        profile: ConnectionProfile,
        credential: BearerCredential
    ): AuthenticatedContext
}

internal fun SecondPassClient.asAuthenticatedConnectionTarget() =
    AuthenticatedConnectionTarget { profile, credential ->
        loadAuthenticatedContext(profile.libraryBaseUrl, credential).also { context ->
            if (context.serverInfo.serverId != profile.serverId) {
                throw SplClientException.ProtocolInvalid("server identity")
            }
        }
    }
