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
    fun `API base change produces different identity`() {
        val profile = profile()

        assertNotEquals(
            profile.authenticatedConnectionIdentity,
            profile.copy(apiBaseUrl = "https://other.example/api/v1/")
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
        serverOrigin = "https://library.example",
        serverBaseUrl = "https://library.example/",
        apiBaseUrl = "https://library.example/api/v1/",
        serverName = "Library",
        serverDescription = "",
        serverVersion = "1",
        serverReleaseDate = "",
        clientSessionId = "client-session",
        clientName = "Tablet",
        clientType = "second-pass-android-client"
    )
}
