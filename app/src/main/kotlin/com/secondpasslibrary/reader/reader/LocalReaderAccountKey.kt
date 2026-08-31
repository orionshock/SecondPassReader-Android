package com.secondpasslibrary.reader.reader

import java.security.MessageDigest
import java.util.Locale

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

        fun from(serverOrigin: String, profileId: String): LocalReaderAccountKey {
            val origin = serverOrigin.trim().trimEnd('/').lowercase(Locale.ROOT)
            val account = profileId.trim()
            require(origin.isNotEmpty()) { "Server origin is required for Reader cache scope." }
            require(account.isNotEmpty()) { "Profile ID is required for Reader cache scope." }
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("$origin\u0000$account".toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte) }
            return LocalReaderAccountKey(digest)
        }

        private const val ACCOUNT_KEY_HEX_LENGTH = 64
        private const val HEX_DIGITS = "0123456789abcdef"
    }
}
