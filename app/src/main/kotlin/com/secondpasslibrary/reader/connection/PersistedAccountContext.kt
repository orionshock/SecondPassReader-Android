package com.secondpasslibrary.reader.connection

import com.secondpasslibrary.reader.app.storage.AccountLocalScope

internal data class PersistedAccountContext(
    val connectionIdentity: AuthenticatedConnectionIdentity,
    val profileId: String,
    val accountServerOrigin: String = connectionIdentity.serverOrigin
) {
    fun matches(profile: ConnectionProfile): Boolean =
        connectionIdentity == profile.authenticatedConnectionIdentity

    fun localDataScope(): AccountLocalScope = AccountLocalScope.from(accountServerOrigin, profileId)
}
