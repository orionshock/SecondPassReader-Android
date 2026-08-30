package com.secondpasslibrary.reader.connection

import java.util.Locale

internal data class AccountLocalDataKey(val serverOrigin: String, val profileId: String) {
    companion object {
        fun from(serverOrigin: String, profileId: String): AccountLocalDataKey {
            val origin = serverOrigin.trim().trimEnd('/').lowercase(Locale.ROOT)
            val profile = profileId.trim()
            require(origin.isNotEmpty()) { "Server origin is required for account-local scope." }
            require(profile.isNotEmpty()) { "Profile ID is required for account-local scope." }
            return AccountLocalDataKey(origin, profile)
        }
    }
}

/** Removes durable, account-scoped app data without owning connection persistence. */
internal fun interface AccountLocalDataCleaner {
    suspend fun purge(account: AccountLocalDataKey)
}

internal fun LocalAccountContext.localDataKey() = persistedAccount.localDataKey()
