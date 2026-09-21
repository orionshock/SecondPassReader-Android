package com.secondpasslibrary.reader.connection

import java.net.URI

internal data class AuthenticatedConnectionIdentity(
    val libraryBaseUrl: String,
    val clientSessionId: String
)

internal val ConnectionProfile.authenticatedConnectionIdentity: AuthenticatedConnectionIdentity
    get() = AuthenticatedConnectionIdentity(libraryBaseUrl, clientSessionId)

internal val AuthenticatedConnectionIdentity.serverOrigin: String
    get() {
        val uri = URI(libraryBaseUrl)
        require(uri.scheme != null && uri.rawAuthority != null) {
            "Library base URL has no server origin."
        }
        return "${uri.scheme}://${uri.rawAuthority}"
    }
