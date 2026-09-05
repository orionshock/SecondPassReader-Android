package com.secondpasslibrary.reader.connection

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.secondpasslibrary.client.DiscoveredServer
import com.secondpasslibrary.reader.design.components.InformationCard
import com.secondpasslibrary.reader.design.components.InformationDetail
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.richtext.ServerRichText

@Composable
internal fun ServerIdentityCard(server: DiscoveredServer) {
    InformationCard("Connected library", icon = AppIcon.ConnectedLibrary) {
        InformationDetail("Name", server.name)
        Text(
            "Description",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium
        )
        if (server.description.isBlank()) {
            Text("—", style = MaterialTheme.typography.bodyLarge)
        } else {
            ServerRichText(
                value = server.description,
                style = MaterialTheme.typography.bodyLarge,
                collapsedMaxLines = 6,
                expandOverflow = true,
                moreLabel = "Show more",
                lessLabel = "Show less"
            )
        }
        InformationDetail(
            "Version",
            listOf(server.version, server.releaseDate)
                .filter(String::isNotBlank)
                .joinToString(" · ")
                .ifBlank { "—" }
        )
        InformationDetail("API", server.apiBaseUrl)
    }
}
