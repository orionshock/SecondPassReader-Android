package com.secondpasslibrary.client.internal.transport

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class WellKnownWire(
    @SerialName("server_name") val serverName: String? = null,
    @SerialName("server_description") val serverDescription: String? = null,
    @SerialName("server_version") val serverVersion: String? = null,
    @SerialName("server_release_date") val serverReleaseDate: String? = null,
    @SerialName("api_base_url") val apiBaseUrl: String? = null
)

@Serializable
internal data class PairingDiscoveryWire(
    @SerialName("discovery_version") val discoveryVersion: String? = null,
    @SerialName("server_name") val serverName: String? = null,
    @SerialName("server_description") val serverDescription: String? = null,
    @SerialName("api_base_url") val apiBaseUrl: String? = null,
    @SerialName("login_request_endpoint") val loginRequestEndpoint: String? = null,
    @SerialName("poll_endpoint_template") val pollEndpointTemplate: String? = null,
    @SerialName("consume_endpoint_template") val consumeEndpointTemplate: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("server_base_url") val serverBaseUrl: String? = null
)

@Serializable
internal data class PairingCreateWire(
    @SerialName("client_name") val clientName: String,
    @SerialName("client_type") val clientType: String
)

@Serializable
internal data class PairingRequestWire(
    val id: String? = null,
    val code: String? = null,
    @SerialName("authorize_url") val authorizeUrl: String? = null,
    @SerialName("poll_url") val pollUrl: String? = null,
    @SerialName("consume_url") val consumeUrl: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    val interval: Long? = null
)

@Serializable
internal data class PairingStatusWire(
    val status: String? = null,
    @SerialName("access_token") val accessToken: String? = null
)

@Serializable
internal data class PairingConsumeWire(
    val status: String? = null,
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("client_session") val clientSession: ClientSessionWire? = null
)

@Serializable
internal data class ClientSessionWire(
    val id: String? = null,
    val name: String? = null,
    @SerialName("client_type") val clientType: String? = null
)

@Serializable
internal data class CurrentUserWire(
    val username: String? = null,
    val email: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("profile_id") val profileId: String? = null,
    val role: String? = null,
    val groups: List<CurrentUserGroupWire>? = null,
    @SerialName("must_change_password") val mustChangePassword: Boolean? = null,
    @SerialName("is_owner") val isOwner: Boolean? = null,
    @SerialName("can_access_django_admin") val canAccessDjangoAdmin: Boolean? = null
)

@Serializable
internal data class CurrentUserGroupWire(
    val id: String? = null,
    val name: String? = null,
    @SerialName("is_public_group") val isPublicGroup: Boolean? = null,
    @SerialName("is_curator") val isCurator: Boolean? = null
)

@Serializable
internal data class ServerInfoWire(
    @SerialName("server_name") val serverName: String? = null,
    @SerialName("server_description") val serverDescription: String? = null,
    @SerialName("server_banner_message") val serverBannerMessage: String? = null,
    @SerialName(
        "advanced_library_groups_enabled"
    ) val advancedLibraryGroupsEnabled: Boolean? = null,
    @SerialName("reading_client_base_url") val readingClientBaseUrl: String? = null,
    @SerialName("marginalia_profile_uri") val marginaliaProfileUri: String? = null,
    @SerialName("public_group") val publicGroup: ServerPublicGroupWire? = null,
    @SerialName("server_version") val serverVersion: String? = null,
    @SerialName("server_release_date") val serverReleaseDate: String? = null
)

@Serializable
internal data class ServerPublicGroupWire(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null
)
