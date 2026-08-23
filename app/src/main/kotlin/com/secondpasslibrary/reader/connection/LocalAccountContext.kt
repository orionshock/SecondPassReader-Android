package com.secondpasslibrary.reader.connection

internal data class LocalAccountContext(
    val profile: ConnectionProfile,
    val persistedAccount: PersistedAccountContext
)
