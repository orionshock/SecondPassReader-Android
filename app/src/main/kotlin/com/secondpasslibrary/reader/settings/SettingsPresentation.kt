package com.secondpasslibrary.reader.settings

import com.secondpasslibrary.client.AuthenticatedContext
import com.secondpasslibrary.reader.connection.ConnectionProfile
import java.net.URI

internal enum class SettingsConnectionStatus(val label: String) {
    CONNECTED("Connected"),
    RECONNECTING("Reconnecting"),
    OFFLINE("Offline"),
    AUTHENTICATION_REQUIRED("Sign-in required")
}

internal data class SettingsUserPresentation(
    val displayName: String,
    val username: String,
    val role: String,
    val email: String?
)

internal data class SettingsLibraryGroupPresentation(val name: String, val description: String?)

internal data class SettingsPresentation(
    val libraryName: String,
    val serverHost: String,
    val status: SettingsConnectionStatus,
    val user: SettingsUserPresentation?,
    val clientName: String,
    val serverDescription: String?,
    val serverBannerMessage: String?,
    val publicGroup: SettingsLibraryGroupPresentation?
)

internal data class SettingsActionAvailability(
    val reconnect: Boolean,
    val retry: Boolean,
    val logout: Boolean,
    val forget: Boolean = true
)

internal fun SettingsConnectionStatus.actionAvailability(): SettingsActionAvailability =
    when (this) {
        SettingsConnectionStatus.CONNECTED ->
            SettingsActionAvailability(reconnect = false, retry = false, logout = true)

        SettingsConnectionStatus.RECONNECTING ->
            SettingsActionAvailability(reconnect = false, retry = false, logout = false)

        SettingsConnectionStatus.OFFLINE ->
            SettingsActionAvailability(reconnect = false, retry = true, logout = false)

        SettingsConnectionStatus.AUTHENTICATION_REQUIRED ->
            SettingsActionAvailability(reconnect = true, retry = false, logout = false)
    }

internal fun settingsPresentation(
    profile: ConnectionProfile,
    context: AuthenticatedContext?,
    status: SettingsConnectionStatus
): SettingsPresentation = SettingsPresentation(
    libraryName = context?.serverInfo?.name?.ifBlank { null } ?: profile.serverName,
    serverHost = serverHostLabel(profile.serverOrigin),
    status = status,
    user = context?.toUserPresentation(),
    clientName = profile.clientName,
    serverDescription =
        (context?.serverInfo?.description ?: profile.serverDescription).takeIf(String::isNotBlank),
    serverBannerMessage = context?.serverInfo?.bannerMessage?.takeIf(String::isNotBlank),
    publicGroup = context?.serverInfo?.publicGroup?.let {
        SettingsLibraryGroupPresentation(
            name = it.name,
            description = it.description.takeIf(String::isNotBlank)
        )
    }
)

private fun AuthenticatedContext.toUserPresentation() = SettingsUserPresentation(
    displayName = currentUser.displayName,
    username = "@${currentUser.username}",
    role = currentUser.role.replaceFirstChar(Char::titlecase),
    email = currentUser.email.takeIf(String::isNotBlank)
)

private fun serverHostLabel(origin: String): String = runCatching {
    val uri = URI(origin)
    val host = uri.host ?: return@runCatching origin
    val defaultPort =
        (uri.scheme.equals("https", ignoreCase = true) && uri.port == 443) ||
            (uri.scheme.equals("http", ignoreCase = true) && uri.port == 80)
    if (uri.port < 0 || defaultPort) host else "$host:${uri.port}"
}.getOrDefault(origin)
