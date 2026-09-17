package com.secondpasslibrary.reader.app.storage

import java.security.MessageDigest
import java.util.Locale

/** Canonical normalized identity for the one account-local footprint retained by the app. */
@ConsistentCopyVisibility
internal data class AccountLocalScope private constructor(
    val serverOrigin: String,
    val profileId: String
) {
    val storageKey: String = MessageDigest.getInstance("SHA-256")
        .digest("$serverOrigin\u0000$profileId".toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    companion object {
        fun from(serverOrigin: String, profileId: String): AccountLocalScope {
            val origin = serverOrigin.trim().trimEnd('/').lowercase(Locale.ROOT)
            val profile = profileId.trim()
            require(origin.isNotEmpty()) { "Server origin is required for account-local scope." }
            require(profile.isNotEmpty()) { "Profile ID is required for account-local scope." }
            return AccountLocalScope(origin, profile)
        }
    }
}
