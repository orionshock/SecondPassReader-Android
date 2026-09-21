package com.secondpasslibrary.client.internal.transport

import com.secondpasslibrary.client.AuthenticatedServerInfo
import com.secondpasslibrary.client.BearerCredential
import com.secondpasslibrary.client.ClientSession
import com.secondpasslibrary.client.CurrentUser
import com.secondpasslibrary.client.CurrentUserGroup
import com.secondpasslibrary.client.PairingConsumption
import com.secondpasslibrary.client.PairingRequest
import com.secondpasslibrary.client.PairingStatus
import com.secondpasslibrary.client.ServerPublicGroup
import com.secondpasslibrary.client.SplClientException

internal fun PairingRequestWire.toPairingRequest(): PairingRequest {
    id.required("pairing creation")
    val pollingInterval = interval ?: invalidProtocol("pairing creation")
    if (pollingInterval <= 0) invalidProtocol("pairing creation")
    return PairingRequest(
        code = code.required("pairing creation"),
        authorizeUrl = requireAbsoluteHttpUrl(authorizeUrl, "pairing creation"),
        pollUrl = requireAbsoluteHttpUrl(pollUrl, "pairing creation"),
        consumeUrl = requireAbsoluteHttpUrl(consumeUrl, "pairing creation"),
        expiresAt = expiresAt.required("pairing creation"),
        intervalSeconds = pollingInterval
    )
}

internal fun PairingStatusWire.toPairingStatus(): PairingStatus {
    if (accessToken != null) invalidProtocol("pairing status")
    return when (status) {
        "pending" -> PairingStatus.PENDING
        "approved" -> PairingStatus.APPROVED
        "denied" -> PairingStatus.DENIED
        "expired" -> PairingStatus.EXPIRED
        "consumed" -> PairingStatus.CONSUMED
        else -> invalidProtocol("pairing status")
    }
}

internal fun PairingConsumeWire.toPairingConsumption(): PairingConsumption = when (status) {
    "consumed" -> mapConsumed(this)
    else -> throw SplClientException.AmbiguousConsumeFailure()
}

private fun mapConsumed(wire: PairingConsumeWire): PairingConsumption {
    val token =
        wire.accessToken?.takeIf(String::isNotBlank) ?: return PairingConsumption.AlreadyConsumed
    val tokenType = wire.tokenType.required("pairing consumption")
    if (!tokenType.equals(
            "Bearer",
            ignoreCase = true
        )
    ) {
        throw SplClientException.AmbiguousConsumeFailure()
    }
    val session = wire.clientSession ?: throw SplClientException.AmbiguousConsumeFailure()
    return PairingConsumption.CredentialIssued(
        credential = BearerCredential.issued(token, "Bearer"),
        clientSession =
            ClientSession(
                id = session.id.required("pairing consumption"),
                name = session.name.required("pairing consumption"),
                clientType = session.clientType.required("pairing consumption")
            )
    )
}

internal fun CurrentUserWire.toModel(): CurrentUser = CurrentUser(
    username = username.orEmpty(),
    email = email.orEmpty(),
    firstName = firstName.orEmpty(),
    lastName = lastName.orEmpty(),
    profileId = profileId.orEmpty(),
    role = role.orEmpty(),
    groups =
        groups.orEmpty().map {
            CurrentUserGroup(
                id = it.id.orEmpty(),
                name = it.name.orEmpty(),
                isPublicGroup = it.isPublicGroup == true,
                isCurator = it.isCurator
            )
        },
    mustChangePassword = mustChangePassword,
    isOwner = isOwner,
    canAccessDjangoAdmin = canAccessDjangoAdmin
)

internal fun ServerInfoWire.toModel(): AuthenticatedServerInfo = AuthenticatedServerInfo(
    serverId = requireAuthenticatedServerId(serverId),
    serverUrls = requireServerUrls(serverUrls),
    name = serverName.orEmpty(),
    description = serverDescription.orEmpty(),
    bannerMessage = serverBannerMessage.orEmpty(),
    advancedLibraryGroupsEnabled = advancedLibraryGroupsEnabled == true,
    readingClientBaseUrl = readingClientBaseUrl,
    marginaliaProfileUri = marginaliaProfileUri.orEmpty(),
    publicGroup =
        publicGroup?.let {
            ServerPublicGroup(it.id.orEmpty(), it.name.orEmpty(), it.description.orEmpty())
        },
    version = serverVersion.orEmpty(),
    releaseDate = serverReleaseDate.orEmpty()
)

private fun requireServerUrls(urls: List<String>?): List<String> =
    urls?.takeIf(List<String>::isNotEmpty)?.map { url ->
        requireLibraryBaseUrl(url, "server info")
    } ?: invalidProtocol("server info")
