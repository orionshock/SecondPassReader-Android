package com.secondpasslibrary.reader.connection

/** Stable authenticated account identity; transport and device-session IDs are separate. */
internal data class AuthenticatedConnectionIdentity(val serverId: String, val profileId: String)

/** Invalidates in-flight feature work when the bearer client session changes. */
internal data class AuthenticatedSessionIdentity(val serverId: String, val clientSessionId: String)

internal fun ConnectionProfile.authenticatedConnectionIdentity(profileId: String) =
    AuthenticatedConnectionIdentity(serverId, profileId)

internal val ConnectionProfile.authenticatedSessionIdentity: AuthenticatedSessionIdentity
    get() = AuthenticatedSessionIdentity(serverId, clientSessionId)
