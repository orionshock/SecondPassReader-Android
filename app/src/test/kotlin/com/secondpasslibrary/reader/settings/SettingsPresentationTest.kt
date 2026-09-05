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
    fun `technical details contain support identifiers but no credential`() {
        val details =
            settingsPresentation(profile(), context(), SettingsConnectionStatus.CONNECTED)
                .technicalDetails
                .associate { it.label to it.value }

        assertEquals("profile-1", details["Profile ID"])
        assertEquals("session-1", details["Client session ID"])
        assertEquals("reader", details["Client type"])
        assertFalse(details.keys.any { it.contains("token", ignoreCase = true) })
        assertFalse(details.values.any { it.contains("spl_secret") })
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
        assertFalse(presentation.technicalDetails.any { it.label == "Profile ID" })
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
        serverOrigin = "https://library.example:443",
        serverBaseUrl = "https://library.example/",
        apiBaseUrl = "https://library.example/api/v1/",
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
