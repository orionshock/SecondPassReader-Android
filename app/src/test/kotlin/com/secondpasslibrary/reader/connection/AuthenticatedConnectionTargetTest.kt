package com.secondpasslibrary.reader.connection

import com.secondpasslibrary.client.AuthenticatedContext
import com.secondpasslibrary.client.AuthenticatedServerInfo
import com.secondpasslibrary.client.BearerCredential
import com.secondpasslibrary.client.CurrentUser
import com.secondpasslibrary.client.DiscoveredServer
import com.secondpasslibrary.client.PairingConsumption
import com.secondpasslibrary.client.PairingRequest
import com.secondpasslibrary.client.PairingStatus
import com.secondpasslibrary.client.SecondPassClient
import com.secondpasslibrary.client.SplClientException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test

internal class AuthenticatedConnectionTargetTest {
    @Test
    fun `verification rejects another server at the saved Library URL`() {
        val profile = ConnectionProfile(
            serverId = "a6722b5a-7982-4778-8c74-39be4241a654",
            serverOrigin = "https://library.example",
            libraryBaseUrl = "https://library.example",
            serverName = "Library",
            serverDescription = "",
            serverVersion = "1",
            serverReleaseDate = "",
            clientSessionId = "session-1",
            clientName = "Tablet",
            clientType = "android"
        )
        val client = object : SecondPassClient {
            override suspend fun discoverServer(userInput: String): DiscoveredServer =
                error("unused")
            override suspend fun beginPairing(
                server: DiscoveredServer,
                clientName: String,
                clientType: String
            ): PairingRequest = error("unused")
            override suspend fun checkPairing(request: PairingRequest): PairingStatus =
                error("unused")
            override suspend fun consumeApprovedPairing(
                request: PairingRequest
            ): PairingConsumption = error("unused")
            override suspend fun loadAuthenticatedContext(
                libraryBaseUrl: String,
                credential: BearerCredential
            ): AuthenticatedContext = AuthenticatedContext(
                CurrentUser(
                    "reader", "", "", "", "profile-1", "", emptyList(), null, null, null
                ),
                AuthenticatedServerInfo(
                    "b6722b5a-7982-4778-8c74-39be4241a654",
                    listOf(libraryBaseUrl),
                    "Library", "", "", false, null, "", null, "1", ""
                )
            )
        }

        assertThrows(SplClientException.ProtocolInvalid::class.java) {
            runBlocking {
                client.asAuthenticatedConnectionTarget().loadContext(
                    profile,
                    BearerCredential.restore("secret")
                )
            }
        }
    }
}
