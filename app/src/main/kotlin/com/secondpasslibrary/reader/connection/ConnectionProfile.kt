package com.secondpasslibrary.reader.connection

import com.secondpasslibrary.client.ClientSession
import com.secondpasslibrary.client.DiscoveredServer

data class ConnectionProfile(
    val serverId: String,
    val serverOrigin: String,
    val libraryBaseUrl: String,
    val serverName: String,
    val serverDescription: String,
    val serverVersion: String,
    val serverReleaseDate: String,
    val clientSessionId: String,
    val clientName: String,
    val clientType: String
) {
    companion object {
        fun linked(server: DiscoveredServer, session: ClientSession): ConnectionProfile =
            ConnectionProfile(
                serverId = server.serverId,
                serverOrigin = server.serverOrigin.value,
                libraryBaseUrl = server.libraryBaseUrl,
                serverName = server.name,
                serverDescription = server.description,
                serverVersion = server.version,
                serverReleaseDate = server.releaseDate,
                clientSessionId = session.id,
                clientName = session.name,
                clientType = session.clientType
            )
    }
}
