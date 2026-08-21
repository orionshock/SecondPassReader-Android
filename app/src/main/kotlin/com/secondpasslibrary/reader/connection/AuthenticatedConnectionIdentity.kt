package com.secondpasslibrary.reader.connection

internal data class AuthenticatedConnectionIdentity(
    val apiBaseUrl: String,
    val clientSessionId: String
)

internal val ConnectionProfile.authenticatedConnectionIdentity: AuthenticatedConnectionIdentity
    get() = AuthenticatedConnectionIdentity(apiBaseUrl, clientSessionId)
