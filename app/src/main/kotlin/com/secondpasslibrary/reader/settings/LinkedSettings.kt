package com.secondpasslibrary.reader.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.client.AuthenticatedContext
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.design.components.InformationCard
import com.secondpasslibrary.reader.design.components.InformationDetail
import com.secondpasslibrary.reader.design.icons.AppIcon

@Composable
fun LinkedSettings(profile: ConnectionProfile, context: AuthenticatedContext) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Connection and account",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Verified library, profile, and device details for this client.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge
        )
        context.serverInfo.bannerMessage.takeIf(String::isNotBlank)?.let { banner ->
            InformationCard("Server banner") { Text(banner) }
        }
        LibraryStatus(profile, context)
        AccountStatus(context)
        DeviceStatus(profile)
    }
}

@Composable
private fun LibraryStatus(profile: ConnectionProfile, context: AuthenticatedContext) {
    InformationCard("Connected library", icon = AppIcon.ConnectedLibrary) {
        InformationDetail("Name", context.serverInfo.name.ifBlank { profile.serverName })
        InformationDetail("Description", context.serverInfo.description.ifBlank { "—" })
        InformationDetail(
            "Version",
            listOf(context.serverInfo.version, context.serverInfo.releaseDate)
                .filter(String::isNotBlank)
                .joinToString(" · ")
                .ifBlank { "—" }
        )
        InformationDetail("Server", profile.serverBaseUrl)
        InformationDetail("Public group", context.serverInfo.publicGroup?.name ?: "—")
        InformationDetail(
            "Advanced groups",
            if (context.serverInfo.advancedLibraryGroupsEnabled) "Enabled" else "Disabled"
        )
    }
}

@Composable
private fun AccountStatus(context: AuthenticatedContext) {
    InformationCard("Signed in", icon = AppIcon.Profile) {
        InformationDetail("Name", context.currentUser.displayName)
        InformationDetail("Username", context.currentUser.username)
        InformationDetail("Email", context.currentUser.email.ifBlank { "—" })
        InformationDetail("Profile ID", context.currentUser.profileId.ifBlank { "—" })
        InformationDetail("Role", context.currentUser.role.ifBlank { "—" })
        context.currentUser.isOwner?.let { InformationDetail("Owner", if (it) "Yes" else "No") }
        InformationDetail(
            "Groups",
            context.currentUser.groups
                .joinToString { group ->
                    group.name + if (group.isCurator == true) " (curator)" else ""
                }.ifBlank { "—" }
        )
    }
}

@Composable
private fun DeviceStatus(profile: ConnectionProfile) {
    InformationCard("This device", icon = AppIcon.Success) {
        InformationDetail("Client name", profile.clientName)
        InformationDetail("Client type", profile.clientType)
        InformationDetail("Session ID", profile.clientSessionId)
        InformationDetail("Connection status", "Verified")
    }
}
