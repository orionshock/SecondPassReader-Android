package com.secondpasslibrary.reader.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistedAccountContextTest {
    @Test
    fun `descriptor matches saved server and client session`() {
        val profile = profile()
        val context = PersistedAccountContext(profile, "profile-1")

        assertTrue(context.matches(profile))
        assertFalse(context.matches(profile.copy(clientSessionId = "new-session")))
        assertFalse(
            context.matches(profile.copy(serverId = "b6722b5a-7982-4778-8c74-39be4241a654"))
        )
    }

    @Test
    fun `local scope ignores route and client session changes`() {
        val profile = profile()
        val original = PersistedAccountContext(profile, "profile-1")
        val changed = PersistedAccountContext(
            profile.copy(
                serverOrigin = "https://alternate.example",
                libraryBaseUrl = "https://alternate.example",
                clientSessionId = "new-session"
            ),
            "profile-1"
        )

        assertEquals(original.localDataScope(), changed.localDataScope())
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
