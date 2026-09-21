package com.secondpasslibrary.client

data class PublicServerInfo(
    val serverId: String,
    val name: String,
    val description: String,
    val version: String,
    val releaseDate: String
)

data class DiscoveredServer(
    val serverOrigin: ServerOrigin,
    val serverId: String,
    val libraryBaseUrl: String,
    val name: String,
    val description: String,
    val version: String,
    val releaseDate: String,
    val discoveryVersion: String,
    val loginRequestUrl: String,
    val tokenType: String
)

data class PairingRequest(
    val code: String,
    val authorizeUrl: String,
    val pollUrl: String,
    val consumeUrl: String,
    val expiresAt: String,
    val intervalSeconds: Long
)

enum class PairingStatus {
    PENDING,
    APPROVED,
    DENIED,
    EXPIRED,
    CONSUMED
}

sealed interface PairingConsumption {
    data class CredentialIssued(
        val credential: BearerCredential,
        val clientSession: ClientSession
    ) : PairingConsumption

    data object AlreadyConsumed : PairingConsumption
}

data class ClientSession(val id: String, val name: String, val clientType: String)

class BearerCredential private constructor(private val secret: String, val tokenType: String) {
    init {
        require(secret.isNotBlank()) { "Credential must not be blank." }
        require(tokenType.isNotBlank()) { "Token type must not be blank." }
    }

    fun <T> useSecret(block: (String) -> T): T = block(secret)

    override fun toString(): String = "BearerCredential([redacted])"

    companion object {
        fun restore(secret: String, tokenType: String = "Bearer"): BearerCredential =
            BearerCredential(secret, tokenType)

        internal fun issued(secret: String, tokenType: String): BearerCredential =
            BearerCredential(secret, tokenType)
    }
}

data class CurrentUser(
    val username: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val profileId: String,
    val role: String,
    val groups: List<CurrentUserGroup>,
    val mustChangePassword: Boolean?,
    val isOwner: Boolean?,
    val canAccessDjangoAdmin: Boolean?
) {
    val displayName: String
        get() = listOf(firstName, lastName).filter(String::isNotBlank).joinToString(" ").ifBlank {
            username
        }
}

data class CurrentUserGroup(
    val id: String,
    val name: String,
    val isPublicGroup: Boolean,
    val isCurator: Boolean?
)

data class AuthenticatedServerInfo(
    val serverId: String,
    val serverUrls: List<String>,
    val name: String,
    val description: String,
    val bannerMessage: String,
    val advancedLibraryGroupsEnabled: Boolean,
    val readingClientBaseUrl: String?,
    val marginaliaProfileUri: String,
    val publicGroup: ServerPublicGroup?,
    val version: String,
    val releaseDate: String
)

data class ServerPublicGroup(val id: String, val name: String, val description: String)

data class AuthenticatedContext(
    val currentUser: CurrentUser,
    val serverInfo: AuthenticatedServerInfo
)
