package com.secondpasslibrary.reader.reader

import com.secondpasslibrary.reader.app.storage.AccountLocalScope

/** Stable, account-local scope shared by Reader persistence and synchronization boundaries. */
@JvmInline
internal value class LocalReaderAccountKey private constructor(val value: String) {
    companion object {
        internal fun fromPersistedValue(value: String): LocalReaderAccountKey {
            require(value.length == ACCOUNT_KEY_HEX_LENGTH && value.all { it in HEX_DIGITS }) {
                "Invalid persisted Reader account scope."
            }
            return LocalReaderAccountKey(value)
        }

        fun from(serverId: String, profileId: String): LocalReaderAccountKey =
            from(AccountLocalScope.from(serverId, profileId))

        fun from(account: AccountLocalScope) = LocalReaderAccountKey(account.storageKey)

        private const val ACCOUNT_KEY_HEX_LENGTH = 64
        private const val HEX_DIGITS = "0123456789abcdef"
    }
}
