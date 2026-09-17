package com.secondpasslibrary.reader.connection

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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
            updateServerUrl = {}, verifyServer = {}, updateClientName = {},
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
}
