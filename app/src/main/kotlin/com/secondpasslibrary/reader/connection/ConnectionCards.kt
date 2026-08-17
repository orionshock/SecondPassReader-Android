package com.secondpasslibrary.reader.connection

import androidx.compose.runtime.Composable
import com.secondpasslibrary.client.DiscoveredServer
import com.secondpasslibrary.reader.design.components.InformationCard
import com.secondpasslibrary.reader.design.components.InformationDetail
import com.secondpasslibrary.reader.design.icons.AppIcon

@Composable
internal fun ServerIdentityCard(server: DiscoveredServer) {
    InformationCard("Connected library", icon = AppIcon.ConnectedLibrary) {
        InformationDetail("Name", server.name)
        InformationDetail("Description", server.description.ifBlank { "—" })
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
