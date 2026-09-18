package com.secondpasslibrary.reader.connection

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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
        compose.onNodeWithText("Enter this code in My Library.").assertIsDisplayed()
        compose.onNodeWithText(state.code).assertIsDisplayed()
        compose.onNodeWithText(state.expiresAt).assertIsDisplayed()
        compose.onNodeWithText(state.statusText).assertIsDisplayed()
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
                                installationId = "a6722b5a-7982-4778-8c74-39be4241a654",
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
        compose.onNodeWithText("Second Pass Library").assertIsDisplayed()
        compose.onNodeWithText("Test Deploy, this is the description line").assertIsDisplayed()
        compose.onAllNodesWithText("Use this address").assertCountEquals(0)
        suggestion.performClick()

        compose.onNodeWithTag("library-address-field").assertTextContains(url)
        assertEquals(0, confirmations)
        assertEquals(0, pairings)
        compose.onNodeWithText("Check address").performClick()
        assertEquals(1, confirmations)
        assertEquals(0, pairings)
    }
}
