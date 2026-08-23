package com.secondpasslibrary.reader.connection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistedAccountContextTest {
    @Test
    fun `matching connection identity is accepted`() {
        val profile = profile()
        val context = PersistedAccountContext(profile.authenticatedConnectionIdentity, "profile-1")

        assertTrue(context.matches(profile))
    }

    @Test
    fun `different connection identity is rejected`() {
        val profile = profile()
        val context = PersistedAccountContext(profile.authenticatedConnectionIdentity, "profile-1")

        assertFalse(context.matches(profile.copy(clientSessionId = "new-session")))
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
