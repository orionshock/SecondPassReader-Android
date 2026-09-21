package com.secondpasslibrary.reader.app.storage

import java.security.MessageDigest
import java.util.UUID

/** Canonical normalized identity for the one account-local footprint retained by the app. */
@ConsistentCopyVisibility
internal data class AccountLocalScope private constructor(
    val serverId: String,
    val profileId: String
) {
    val storageKey: String = MessageDigest.getInstance("SHA-256")
        .digest("$serverId\u0000$profileId".toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    companion object {
        fun from(serverId: String, profileId: String): AccountLocalScope {
            val canonicalServerId = try {
                UUID.fromString(serverId.trim()).toString()
            } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException(
                    "A valid server ID is required for account-local scope."
                )
            }
            val profile = profileId.trim()
            require(profile.isNotEmpty()) { "Profile ID is required for account-local scope." }
            return AccountLocalScope(canonicalServerId, profile)
        }
    }
}
