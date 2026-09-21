package com.secondpasslibrary.reader.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AuthenticatedConnectionIdentityTest {
    @Test
    fun `equal connection fields produce equal identity`() {
        val profile = profile()

        assertEquals(
            profile.authenticatedConnectionIdentity,
            profile.copy(serverName = "Renamed", clientName = "Other tablet")
                .authenticatedConnectionIdentity
        )
    }

    @Test
    fun `Library URL change produces different saved connection identity`() {
        val profile = profile()

        assertNotEquals(
            profile.authenticatedConnectionIdentity,
            profile.copy(libraryBaseUrl = "https://other.example")
                .authenticatedConnectionIdentity
        )
    }

    @Test
    fun `re-pairing to a new client session produces different identity`() {
        val profile = profile()

        assertNotEquals(
            profile.authenticatedConnectionIdentity,
            profile.copy(clientSessionId = "replacement-session")
                .authenticatedConnectionIdentity
        )
    }

    private fun profile() = ConnectionProfile(
        serverId = "a6722b5a-7982-4778-8c74-39be4241a654",
        serverOrigin = "https://library.example",
        libraryBaseUrl = "https://library.example",
        serverName = "Library",
        serverDescription = "",
        serverVersion = "1",
        serverReleaseDate = "",
        clientSessionId = "client-session",
        clientName = "Tablet",
        clientType = "second-pass-android-client"
    )
}
