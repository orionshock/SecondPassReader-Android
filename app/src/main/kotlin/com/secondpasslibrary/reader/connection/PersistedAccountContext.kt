package com.secondpasslibrary.reader.connection

import com.secondpasslibrary.reader.app.storage.AccountLocalScope

internal data class PersistedAccountContext(
    val connectionIdentity: AuthenticatedConnectionIdentity,
    val clientSessionId: String
) {
    constructor(profile: ConnectionProfile, profileId: String) : this(
        profile.authenticatedConnectionIdentity(profileId),
        profile.clientSessionId
    )

    val profileId: String get() = connectionIdentity.profileId

    fun matches(profile: ConnectionProfile): Boolean =
        connectionIdentity.serverId == profile.serverId &&
            clientSessionId == profile.clientSessionId

    fun localDataScope(): AccountLocalScope =
        AccountLocalScope.from(connectionIdentity.serverId, profileId)
}
