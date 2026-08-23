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

internal data class SettingsTechnicalDetail(val label: String, val value: String)

internal data class SettingsPresentation(
    val libraryName: String,
    val serverHost: String,
    val status: SettingsConnectionStatus,
    val user: SettingsUserPresentation?,
    val clientName: String,
    val technicalDetails: List<SettingsTechnicalDetail>
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
    technicalDetails = technicalDetails(profile, context)
)

private fun AuthenticatedContext.toUserPresentation() = SettingsUserPresentation(
    displayName = currentUser.displayName,
    username = "@${currentUser.username}",
    role = currentUser.role.replaceFirstChar(Char::titlecase),
    email = currentUser.email.takeIf(String::isNotBlank)
)

private fun technicalDetails(
    profile: ConnectionProfile,
    context: AuthenticatedContext?
): List<SettingsTechnicalDetail> = buildList {
    add(SettingsTechnicalDetail("Server origin", profile.serverOrigin))
    val version = context?.serverInfo?.version?.ifBlank { null } ?: profile.serverVersion
    version.takeIf(String::isNotBlank)?.let {
        add(SettingsTechnicalDetail("Server version", it))
    }
    add(SettingsTechnicalDetail("Client type", profile.clientType))
    add(SettingsTechnicalDetail("Client session ID", profile.clientSessionId))
    context?.currentUser?.profileId?.takeIf(String::isNotBlank)?.let {
        add(SettingsTechnicalDetail("Profile ID", it))
    }
    context?.serverInfo?.advancedLibraryGroupsEnabled?.let {
        add(SettingsTechnicalDetail("Advanced groups", if (it) "Enabled" else "Disabled"))
    }
}

private fun serverHostLabel(origin: String): String = runCatching {
    val uri = URI(origin)
    val host = uri.host ?: return@runCatching origin
    val defaultPort =
        (uri.scheme.equals("https", ignoreCase = true) && uri.port == 443) ||
            (uri.scheme.equals("http", ignoreCase = true) && uri.port == 80)
    if (uri.port < 0 || defaultPort) host else "$host:${uri.port}"
}.getOrDefault(origin)
