package com.secondpasslibrary.reader.settings

import com.secondpasslibrary.client.AuthenticatedContext
import com.secondpasslibrary.client.AuthenticatedServerInfo
import com.secondpasslibrary.client.CurrentUser
import com.secondpasslibrary.client.ServerPublicGroup
import com.secondpasslibrary.reader.connection.ConnectionProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPresentationTest {
    @Test
    fun `primary settings expose useful identity without protocol identifiers`() {
        val presentation =
            settingsPresentation(profile(), context(), SettingsConnectionStatus.CONNECTED)

        assertEquals("Second Pass Library", presentation.libraryName)
        assertEquals("library.example", presentation.serverHost)
        assertEquals("Reader Name", presentation.user?.displayName)
        assertEquals("@reader", presentation.user?.username)
        assertEquals("Reader", presentation.user?.role)
        assertEquals("Pixel Tablet", presentation.clientName)
    }

    @Test
    fun `server descriptive markup remains unchanged for presentation`() {
        val presentation =
            settingsPresentation(profile(), context(), SettingsConnectionStatus.CONNECTED)

        assertEquals("<p>Server &amp; description.</p>", presentation.serverDescription)
        assertEquals("<strong>Maintenance</strong><br>Tonight", presentation.serverBannerMessage)
        assertEquals("Common Room", presentation.publicGroup?.name)
        assertEquals("<ul><li>Shared books</li></ul>", presentation.publicGroup?.description)
    }

    @Test
    fun `unverified settings omit account identity rather than fabricating it`() {
        val presentation =
            settingsPresentation(
                profile(),
                context = null,
                SettingsConnectionStatus.AUTHENTICATION_REQUIRED
            )

        assertNull(presentation.user)
    }

    @Test
    fun `connection actions follow authority state`() {
        assertTrue(SettingsConnectionStatus.CONNECTED.actionAvailability().logout)
        assertTrue(
            SettingsConnectionStatus.AUTHENTICATION_REQUIRED.actionAvailability().reconnect
        )
        assertTrue(SettingsConnectionStatus.OFFLINE.actionAvailability().retry)
        assertFalse(SettingsConnectionStatus.RECONNECTING.actionAvailability().logout)
    }

    private fun profile() = ConnectionProfile(
        serverId = "a6722b5a-7982-4778-8c74-39be4241a654",
        serverOrigin = "https://library.example:443",
        libraryBaseUrl = "https://library.example",
        serverName = "Library",
        serverDescription = "Description",
        serverVersion = "1.0",
        serverReleaseDate = "2026-08-23",
        clientSessionId = "session-1",
        clientName = "Pixel Tablet",
        clientType = "reader"
    )

    private fun context() = AuthenticatedContext(
        currentUser =
            CurrentUser(
                username = "reader",
                email = "reader@example.com",
                firstName = "Reader",
                lastName = "Name",
                profileId = "profile-1",
                role = "reader",
                groups = emptyList(),
                mustChangePassword = false,
                isOwner = false,
                canAccessDjangoAdmin = false
            ),
        serverInfo =
            AuthenticatedServerInfo(
                serverId = "a6722b5a-7982-4778-8c74-39be4241a654",
                serverUrls = listOf("https://library.example"),
                name = "Second Pass Library",
                description = "<p>Server &amp; description.</p>",
                bannerMessage = "<strong>Maintenance</strong><br>Tonight",
                advancedLibraryGroupsEnabled = true,
                readingClientBaseUrl = null,
                marginaliaProfileUri = "https://library.example/profile",
                publicGroup =
                    ServerPublicGroup(
                        id = "public-1",
                        name = "Common Room",
                        description = "<ul><li>Shared books</li></ul>"
                    ),
                version = "1.2",
                releaseDate = "2026-08-23"
            )
    )
}
