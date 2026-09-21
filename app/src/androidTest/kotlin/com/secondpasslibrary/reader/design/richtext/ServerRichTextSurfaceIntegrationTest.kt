package com.secondpasslibrary.reader.design.richtext

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.client.DiscoveredServer
import com.secondpasslibrary.client.LibraryGroupSummary
import com.secondpasslibrary.client.ServerOrigin
import com.secondpasslibrary.client.Shelf
import com.secondpasslibrary.client.ShelfOwner
import com.secondpasslibrary.client.ShelfVisibility
import com.secondpasslibrary.reader.connection.ServerIdentityCard
import com.secondpasslibrary.reader.design.SecondPassTheme
import com.secondpasslibrary.reader.library.chrome.compactDescription
import com.secondpasslibrary.reader.settings.LibraryAccountSettings
import com.secondpasslibrary.reader.settings.SettingsConnectionStatus
import com.secondpasslibrary.reader.settings.SettingsLibraryGroupPresentation
import com.secondpasslibrary.reader.settings.SettingsPresentation
import com.secondpasslibrary.reader.shelves.detail.ShelfDetailHeader
import com.secondpasslibrary.reader.shelves.detail.ShelfDetailResourceState
import com.secondpasslibrary.reader.shelves.detail.ShelfItemsState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServerRichTextSurfaceIntegrationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun shelfDetailRendersStructuredDescription() {
        compose.setContent {
            SecondPassTheme {
                ShelfDetailHeader(
                    state = ShelfDetailResourceState(shelf = richShelf()),
                    items = ShelfItemsState(),
                    onRetry = {},
                    canManage = false,
                    onManageContents = {},
                    onOrderingSelected = {},
                    onLayoutSelected = {},
                    modifier = Modifier
                )
            }
        }

        compose.onNodeWithText(
            "Shelf & context.\n1. First shelf purpose\n2. Second shelf purpose"
        ).assertExists()
    }

    @Test
    fun serverDescriptionBannerAndPublicGroupUseSharedPresentation() {
        compose.setContent {
            SecondPassTheme {
                Column {
                    ServerIdentityCard(discoveredServer())
                    LibraryAccountSettings(settingsPresentation())
                }
            }
        }

        compose.onNodeWithText("Discovery & pairing description.").assertExists()
        compose.onNodeWithText("Authenticated server & description.").assertExists()
        compose.onNodeWithText("\u2022 Shared books\n\u2022 Shared notes").assertExists()
        compose.onNodeWithText("Maintenance\nTonight").assertExists()
    }

    @Test
    fun compactGroupDescriptionDecodesAndSeparatesListItems() {
        val group = LibraryGroupSummary(
            id = "group-1",
            name = "Common Room",
            isPublicGroup = true,
            description = "<p>Shared &amp; public.</p><ul><li>Books</li><li>Notes</li></ul>"
        )

        assertEquals("Shared & public.\n\u2022 Books\n\u2022 Notes", group.compactDescription())
    }

    private fun richShelf() = Shelf(
        id = "shelf-1",
        name = "Rich Shelf",
        description =
            "<p>Shelf &amp; context.</p>" +
                "<ol><li>First shelf purpose</li><li>Second shelf purpose</li></ol>",
        owner = ShelfOwner.User("profile-1", "reader"),
        visibility = ShelfVisibility.PRIVATE,
        itemCount = 0,
        canEdit = true,
        createdBy = null,
        createdAt = "2026-09-05T00:00:00Z",
        updatedAt = "2026-09-05T00:00:00Z",
        matchedItemId = null,
        previewBooks = emptyList()
    )

    private fun discoveredServer() = DiscoveredServer(
        serverOrigin = ServerOrigin.fromUserInput("https://library.example"),
        serverId = "a6722b5a-7982-4778-8c74-39be4241a654",
        libraryBaseUrl = "https://library.example",
        name = "Library",
        description = "<p>Discovery &amp; pairing description.</p>",
        version = "1",
        releaseDate = "2026-09-05",
        discoveryVersion = "1",
        loginRequestUrl = "https://library.example/pair",
        tokenType = "Bearer"
    )

    private fun settingsPresentation() = SettingsPresentation(
        libraryName = "Library",
        serverHost = "library.example",
        status = SettingsConnectionStatus.CONNECTED,
        user = null,
        clientName = "Pixel Tablet",
        serverDescription = "<p>Authenticated server &amp; description.</p>",
        serverBannerMessage = "<strong>Maintenance</strong><br>Tonight",
        publicGroup =
            SettingsLibraryGroupPresentation(
                "Common Room",
                "<ul><li>Shared books</li><li>Shared notes</li></ul>"
            ),
        technicalDetails = emptyList()
    )
}
