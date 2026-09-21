package com.secondpasslibrary.reader.connection

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.client.DiscoveredServer
import com.secondpasslibrary.client.ServerOrigin
import com.secondpasslibrary.reader.connection.discovery.ConnectionLibrarySuggestion
import com.secondpasslibrary.reader.design.SecondPassTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectionScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun approvalPresentationDisplaysCodeAndOpensOnlyAuthorizationUrl() {
        val opened = mutableListOf<String>()
        var cancellations = 0
        val state = ConnectionUiState.WaitingForApproval(
            serverName = "My Library",
            clientName = "Tablet",
            code = "ABCD-EFGH",
            authorizeUrl = "https://library.example/authorize",
            expiresAt = "2026-09-17T20:00:00Z",
            statusText = "Waiting for approval"
        )
        val actions = ConnectionScreenActions(
            updateServerUrl = {}, selectSuggestedServer = {}, verifyServer = {},
            updateClientName = {},
            beginPairing = {}, relinkLocalAccount = {}, abandonPairing = { cancellations++ },
            retryProfilePersistence = {}, retryStoredVerification = {},
            retryRestore = {}, forgetLocalConnection = {}
        )
        compose.setContent {
            SecondPassTheme {
                CompositionLocalProvider(
                    LocalUriHandler provides object : UriHandler {
                        override fun openUri(uri: String) {
                            opened += uri
                        }
                    }
                ) {
                    ConnectionScreen(state, actions)
                }
            }
        }
        compose.onNodeWithText(state.code).assertIsDisplayed()
        compose.onNodeWithText(ApprovalExpiryPresenter.label(state.expiresAt)).assertIsDisplayed()
        compose.onAllNodesWithText(state.expiresAt).assertCountEquals(0)
        compose.onNodeWithText(state.statusText).assertIsDisplayed()
        assertEquals(0, opened.size)
        assertEquals(0, cancellations)
        assertLeftOf("Cancel", "Open approval page")
        compose.onNodeWithText("Open approval page").performClick()
        assertEquals(listOf(state.authorizeUrl), opened)
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, cancellations)
    }

    @Test
    fun nearbyLibrarySuggestionOnlyPrefillsExistingAddressField() {
        val url = "https://secondpasslibrary.zcaprica.duckdns.org"
        var confirmations = 0
        var pairings = 0
        var state by
            mutableStateOf(
                ConnectionUiState.ServerEntry(
                    suggestions =
                        listOf(
                            ConnectionLibrarySuggestion(
                                serverId = "a6722b5a-7982-4778-8c74-39be4241a654",
                                name = "Second Pass Library",
                                description =
                                    "<p>Test <strong>Deploy</strong>, this is the description line</p>",
                                url = url
                            )
                        )
                )
            )
        val actions = ConnectionScreenActions(
            updateServerUrl = { state = state.copy(serverUrl = it) },
            selectSuggestedServer = { state = state.copy(serverUrl = it) },
            verifyServer = { confirmations += 1 },
            updateClientName = {}, beginPairing = { pairings += 1 }, relinkLocalAccount = {},
            abandonPairing = {}, retryProfilePersistence = {},
            retryStoredVerification = {}, retryRestore = {}, forgetLocalConnection = {}
        )
        compose.setContent {
            SecondPassTheme { ConnectionScreen(state, actions) }
        }

        val suggestion =
            compose.onNode(
                SemanticsMatcher("has Use this address click action") { node ->
                    SemanticsActions.OnClick in node.config &&
                        node.config[SemanticsActions.OnClick].label == "Use this address"
                }
            )
        suggestion.assertIsDisplayed().assertHasClickAction()
        suggestion.assert(
            SemanticsMatcher("describes Library, address, and sanitized description") { node ->
                node.config[SemanticsProperties.ContentDescription].single().let { description ->
                    description.contains("Second Pass Library") &&
                        description.contains(url) &&
                        description.contains("Test Deploy, this is the description line")
                }
            }
        )
        compose.onAllNodesWithText("Use this address").assertCountEquals(0)
        suggestion.performClick()

        compose.onNodeWithTag("library-address-field").assertTextContains(url)
        assertEquals(0, confirmations)
        assertEquals(0, pairings)
        compose.onNodeWithText("Check address").performClick()
        assertEquals(1, confirmations)
        assertEquals(0, pairings)
    }

    @Test
    fun addressErrorStaysWithEditableFieldAndCheckAddressIsRightAligned() {
        val url = "https://not-a-library.example"
        val message = "Address problem"
        var edited = ""
        var checks = 0
        compose.setContent {
            SecondPassTheme {
                ConnectionScreen(
                    ConnectionUiState.ServerEntry(serverUrl = url, message = message),
                    actions(updateServerUrl = { edited = it }, verifyServer = { checks++ })
                )
            }
        }
        compose.onNodeWithTag("library-address-field").assertTextContains(url)
        compose.onNodeWithTag("library-address-field").assert(
            SemanticsMatcher("field exposes error") { node ->
                SemanticsProperties.Error in node.config &&
                    node.config[SemanticsProperties.Error] == message
            }
        )
        compose.onNodeWithText(message).assertIsDisplayed()
        compose.onNodeWithTag("library-address-field")
            .performTextReplacement("https://other.example")
        assertEquals("https://other.example", edited)
        val fieldRight = compose.onNodeWithTag("library-address-field")
            .fetchSemanticsNode().boundsInRoot.right
        val checkRight = compose.onNodeWithText("Check address")
            .fetchSemanticsNode().boundsInRoot.right
        assertEquals(true, checkRight > fieldRight / 2f)
        compose.onNodeWithText("Check address").performClick()
        assertEquals(1, checks)
    }

    @Test
    fun confirmedLibraryKeepsIdentityProminentAndPrimaryActionOnRight() {
        val url = "https://library.example"
        var changedAddress = 0
        var linked = 0
        var deviceName = ""
        val server = DiscoveredServer(
            serverOrigin = ServerOrigin.fromUserInput(url),
            serverId = "a6722b5a-7982-4778-8c74-39be4241a654",
            libraryBaseUrl = "$url",
            name = "My Library",
            description = "<p>A <strong>quiet</strong> library</p>",
            version = "alpha-rc1",
            releaseDate = "2026-09-17",
            discoveryVersion = "1",
            loginRequestUrl = "$url/pair",
            tokenType = "Bearer"
        )
        compose.setContent {
            SecondPassTheme {
                ConnectionScreen(
                    ConnectionUiState.ServerConfirmed(server, "Tablet"),
                    actions(
                        updateClientName = { deviceName = it },
                        beginPairing = { linked++ },
                        abandonPairing = { changedAddress++ }
                    )
                )
            }
        }
        compose.onNodeWithText("My Library").assertIsDisplayed()
        compose.onNodeWithText(url).assertIsDisplayed()
        compose.onAllNodesWithText("alpha-rc1").assertCountEquals(0)
        compose.onAllNodesWithText("$url/api/v1/").assertCountEquals(0)
        assertLeftOf("Change address", "Link device")
        compose.onNodeWithText("Tablet").performTextReplacement("Bedroom tablet")
        assertEquals("Bedroom tablet", deviceName)
        assertEquals(0, linked)
        compose.onNodeWithText("Change address").performClick()
        assertEquals(1, changedAddress)
        compose.onNodeWithText("Link device").performClick()
        assertEquals(1, linked)
    }

    private fun assertLeftOf(left: String, right: String) {
        val leftBounds = compose.onNodeWithText(left).fetchSemanticsNode().boundsInRoot
        val rightBounds = compose.onNodeWithText(right).fetchSemanticsNode().boundsInRoot
        assertEquals(true, leftBounds.right <= rightBounds.left)
    }

    private fun actions(
        updateServerUrl: (String) -> Unit = {},
        verifyServer: () -> Unit = {},
        updateClientName: (String) -> Unit = {},
        beginPairing: () -> Unit = {},
        abandonPairing: () -> Unit = {}
    ) = ConnectionScreenActions(
        updateServerUrl = updateServerUrl,
        selectSuggestedServer = {},
        verifyServer = verifyServer,
        updateClientName = updateClientName,
        beginPairing = beginPairing,
        relinkLocalAccount = {},
        abandonPairing = abandonPairing,
        retryProfilePersistence = {},
        retryStoredVerification = {},
        retryRestore = {},
        forgetLocalConnection = {}
    )
}
