package com.secondpasslibrary.reader.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.client.AuthenticatedContext
import com.secondpasslibrary.client.DiscoveredServer
import com.secondpasslibrary.reader.connection.ConnectionProfile

@Composable
internal fun LinkedContent(profile: ConnectionProfile, context: AuthenticatedContext) {
    SectionTitle(
        "Connected",
        "Authenticated SPL context is verified. This is a temporary development status surface."
    )
    context.serverInfo.bannerMessage.takeIf(String::isNotBlank)?.let { banner ->
        DetailCard("Server banner") { Text(banner) }
    }
    DetailCard("Connected library") {
        Detail("Name", context.serverInfo.name.ifBlank { profile.serverName })
        Detail("Description", context.serverInfo.description.ifBlank { "—" })
        Detail(
            "Version",
            listOf(context.serverInfo.version, context.serverInfo.releaseDate)
                .filter(String::isNotBlank)
                .joinToString(" · ")
                .ifBlank { "—" }
        )
        Detail("Server", profile.serverBaseUrl)
        Detail("Public group", context.serverInfo.publicGroup?.name ?: "—")
        Detail(
            "Advanced groups",
            if (context.serverInfo.advancedLibraryGroupsEnabled) "Enabled" else "Disabled"
        )
    }
    DetailCard("Signed in") {
        Detail("Name", context.currentUser.displayName)
        Detail("Username", context.currentUser.username)
        Detail("Email", context.currentUser.email.ifBlank { "—" })
        Detail("Profile ID", context.currentUser.profileId.ifBlank { "—" })
        Detail("Role", context.currentUser.role.ifBlank { "—" })
        context.currentUser.isOwner?.let { Detail("Owner", if (it) "Yes" else "No") }
        Detail(
            "Groups",
            context.currentUser.groups
                .joinToString { group ->
                    group.name + if (group.isCurator == true) " (curator)" else ""
                }.ifBlank { "—" }
        )
    }
    DetailCard("This device") {
        Detail("Client name", profile.clientName)
        Detail("Client type", profile.clientType)
        Detail("Session ID", profile.clientSessionId)
        Detail("Connection status", "Verified")
    }
}

@Composable
internal fun ServerIdentityCard(server: DiscoveredServer) {
    DetailCard("Connected library") {
        Detail("Name", server.name)
        Detail("Description", server.description.ifBlank { "—" })
        Detail(
            "Version",
            listOf(server.version, server.releaseDate)
                .filter(String::isNotBlank)
                .joinToString(" · ")
                .ifBlank { "—" }
        )
        Detail("API", server.apiBaseUrl)
    }
}

@Composable
internal fun DetailCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge
            )
            content()
        }
    }
}

@Composable
internal fun Detail(label: String, value: String) {
    Column {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium
        )
        SelectionContainer { Text(value, style = MaterialTheme.typography.bodyLarge) }
    }
}
