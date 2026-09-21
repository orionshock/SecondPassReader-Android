package com.secondpasslibrary.reader.connection

import com.secondpasslibrary.client.AuthenticatedContext
import com.secondpasslibrary.client.BearerCredential
import com.secondpasslibrary.client.SecondPassClient
import com.secondpasslibrary.client.SplClientException

internal data class VerifiedConnection(
    val context: AuthenticatedContext,
    val activeLibraryBaseUrl: String
)

/** Connection-owned verification seam; feature clients only receive the selected transport. */
internal fun interface AuthenticatedConnectionTarget {
    suspend fun loadContext(
        profile: ConnectionProfile,
        credential: BearerCredential,
        routes: KnownServerRoutes
    ): VerifiedConnection
}

internal fun SecondPassClient.asAuthenticatedConnectionTarget() =
    AuthenticatedConnectionTarget { profile, credential, routes ->
        require(routes.serverId == profile.serverId)
        var unreachable: SplClientException.ServerUnreachable? = null
        var identityMismatch = false
        for (url in routes.serverUrls) {
            try {
                val context = verifiedContextForRoute(url, routes, credential)
                if (context == null) {
                    identityMismatch = true
                } else {
                    return@AuthenticatedConnectionTarget VerifiedConnection(context, url)
                }
            } catch (failure: SplClientException.ServerUnreachable) {
                unreachable = failure
            }
        }
        if (identityMismatch) throw SplClientException.ProtocolInvalid("server identity")
        throw unreachable ?: SplClientException.ServerUnreachable()
    }

/** Checks public identity before credential use whenever the transport location changes. */
private suspend fun SecondPassClient.verifiedContextForRoute(
    url: String,
    routes: KnownServerRoutes,
    credential: BearerCredential
): AuthenticatedContext? {
    if (url != routes.activeLibraryBaseUrl && identifyServer(url) != routes.serverId) return null
    return loadAuthenticatedContext(url, credential).takeIf {
        it.serverInfo.serverId == routes.serverId
    }
}
