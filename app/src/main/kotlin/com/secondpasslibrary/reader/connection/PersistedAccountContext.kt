package com.secondpasslibrary.reader.connection

internal data class PersistedAccountContext(
    val connectionIdentity: AuthenticatedConnectionIdentity,
    val profileId: String,
    val accountServerOrigin: String = connectionIdentity.serverOrigin
) {
    fun matches(profile: ConnectionProfile): Boolean =
        connectionIdentity == profile.authenticatedConnectionIdentity

    fun localDataKey(): AccountLocalDataKey =
        AccountLocalDataKey.from(accountServerOrigin, profileId)
}
