package com.secondpasslibrary.reader.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AuthenticatedConnectionIdentityTest {
    private val profile = ConnectionProfile(
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

    @Test
    fun `route and client session changes preserve account identity`() {
        val original = profile.authenticatedConnectionIdentity("profile-1")
        val changed = profile.copy(
            serverOrigin = "https://alternate.example",
            libraryBaseUrl = "https://alternate.example",
            clientSessionId = "replacement-session"
        )

        assertEquals(original, changed.authenticatedConnectionIdentity("profile-1"))
        assertNotEquals(profile.authenticatedSessionIdentity, changed.authenticatedSessionIdentity)
    }

    @Test
    fun `server or profile change produces another account identity`() {
        val original = profile.authenticatedConnectionIdentity("profile-1")

        assertNotEquals(original, profile.authenticatedConnectionIdentity("profile-2"))
        assertNotEquals(
            original,
            profile.copy(serverId = "b6722b5a-7982-4778-8c74-39be4241a654")
                .authenticatedConnectionIdentity("profile-1")
        )
    }
}
