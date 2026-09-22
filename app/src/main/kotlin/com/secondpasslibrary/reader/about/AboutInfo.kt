package com.secondpasslibrary.reader.about

import com.secondpasslibrary.client.AuthenticatedContext
import com.secondpasslibrary.reader.app.AppIdentity
import com.secondpasslibrary.reader.connection.ConnectionProfile

internal data class AboutInfo(
    val versionName: String,
    val versionCode: Long,
    val packageId: String,
    val device: String,
    val androidVersion: String,
    val apiLevel: Int,
    val libraryName: String?,
    val serverVersion: String?,
    val serverId: String?,
    val libraryUrl: String?
) {
    val hasLibraryDetails: Boolean
        get() = listOf(libraryName, serverVersion, serverId, libraryUrl).any { it != null }

    fun copyText(): String = buildList {
        add("Second Pass Reader $versionName ($versionCode)")
        add(packageId)
        add("Device: $device")
        add("Android: $androidVersion / API $apiLevel")
        libraryName?.let { add("Library: $it") }
        serverVersion?.let { add("Server version: $it") }
        serverId?.let { add("Server ID: $it") }
        libraryUrl?.let { add("Library URL: $it") }
    }.joinToString("\n")
}

internal fun aboutInfo(
    identity: AppIdentity,
    profile: ConnectionProfile?,
    context: AuthenticatedContext?,
    activeLibraryBaseUrl: String?
): AboutInfo = AboutInfo(
    versionName = identity.versionName,
    versionCode = identity.versionCode,
    packageId = identity.packageId,
    device = identity.device,
    androidVersion = identity.androidVersion,
    apiLevel = identity.apiLevel,
    libraryName = (context?.serverInfo?.name ?: profile?.serverName)?.takeIf(String::isNotBlank),
    serverVersion = (context?.serverInfo?.version ?: profile?.serverVersion)
        ?.takeIf(String::isNotBlank),
    serverId = (context?.serverInfo?.serverId ?: profile?.serverId)
        ?.takeIf(String::isNotBlank),
    libraryUrl = activeLibraryBaseUrl?.takeIf(String::isNotBlank)
)
