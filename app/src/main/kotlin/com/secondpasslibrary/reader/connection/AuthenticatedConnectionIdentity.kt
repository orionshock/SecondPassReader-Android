package com.secondpasslibrary.reader.connection

import java.net.URI

internal data class AuthenticatedConnectionIdentity(
    val apiBaseUrl: String,
    val clientSessionId: String
)

internal val ConnectionProfile.authenticatedConnectionIdentity: AuthenticatedConnectionIdentity
    get() = AuthenticatedConnectionIdentity(apiBaseUrl, clientSessionId)

internal val AuthenticatedConnectionIdentity.serverOrigin: String
    get() {
        val uri = URI(apiBaseUrl)
        require(uri.scheme != null && uri.rawAuthority != null) {
            "Authenticated API base URL has no server origin."
        }
        return "${uri.scheme}://${uri.rawAuthority}"
    }
