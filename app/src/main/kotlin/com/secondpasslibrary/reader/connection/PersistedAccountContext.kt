package com.secondpasslibrary.reader.connection

internal data class PersistedAccountContext(
    val connectionIdentity: AuthenticatedConnectionIdentity,
    val profileId: String
) {
    fun matches(profile: ConnectionProfile): Boolean =
        connectionIdentity == profile.authenticatedConnectionIdentity
}
