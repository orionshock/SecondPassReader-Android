package com.secondpasslibrary.reader.connection

import org.junit.Assert.assertEquals
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

    @Test
    fun `account-local identity ignores client Session rotation`() {
        val profile = profile()
        val original = PersistedAccountContext(
            profile.authenticatedConnectionIdentity,
            "profile-1",
            profile.serverOrigin
        )
        val relinked = PersistedAccountContext(
            profile.copy(clientSessionId = "new-session").authenticatedConnectionIdentity,
            "profile-1",
            profile.serverOrigin
        )

        assertEquals(original.localDataScope(), relinked.localDataScope())
    }

    @Test
    fun `account-local identity distinguishes server and profile`() {
        val profile = profile()
        val current = PersistedAccountContext(
            profile.authenticatedConnectionIdentity,
            "profile-1",
            profile.serverOrigin
        )

        assertFalse(
            current.localDataScope() ==
                current.copy(accountServerOrigin = "https://other.example").localDataScope()
        )
        assertFalse(
            current.localDataScope() == current.copy(profileId = "profile-2").localDataScope()
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
