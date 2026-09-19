package com.secondpasslibrary.reader.connection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun ConnectionScreen(
    state: ConnectionUiState,
    actions: ConnectionScreenActions,
    requestLanDiscoveryAccess: (() -> Unit)? = null
) {
    ConnectionFrame {
        when (state) {
            ConnectionUiState.Restoring -> BusyContent("Reconnecting")

            is ConnectionUiState.ServerEntry ->
                ServerEntryContent(
                    state,
                    actions.updateServerUrl,
                    actions.selectSuggestedServer,
                    actions.verifyServer,
                    requestLanDiscoveryAccess
                )

            is ConnectionUiState.VerifyingServer -> BusyContent("Checking ${state.serverUrl}")

            is ConnectionUiState.ServerConfirmed ->
                ServerConfirmedContent(
                    state,
                    actions.updateClientName,
                    actions.beginPairing,
                    actions.abandonPairing
                )

            is ConnectionUiState.StartingPairing ->
                BusyContent("Connecting to ${state.server.name}")

            is ConnectionUiState.WaitingForApproval ->
                WaitingContent(state, actions.abandonPairing)

            is ConnectionUiState.CompletingPairing ->
                BusyContent("Finishing the connection to ${state.serverName}")

            is ConnectionUiState.PersistenceRecovery,
            is ConnectionUiState.StoredCredentialProblem,
            is ConnectionUiState.RestoreProblem,
            is ConnectionUiState.AuthenticationRequired,
            is ConnectionUiState.LocalStorageProblem -> RecoveryContent(state, actions)

            is ConnectionUiState.TerminalPairingProblem ->
                ProblemContent(
                    "Device not linked",
                    state.message,
                    "Start again",
                    actions.abandonPairing
                )

            is ConnectionUiState.Linked -> error("ConnectionScreen cannot render linked state.")
        }
    }
}

@Composable
private fun RecoveryContent(state: ConnectionUiState, actions: ConnectionScreenActions) {
    when (state) {
        is ConnectionUiState.PersistenceRecovery ->
            ProblemContent(
                "Connection not saved",
                state.message,
                "Retry",
                actions.retryProfilePersistence,
                actions.forgetLocalConnection
            )

        is ConnectionUiState.StoredCredentialProblem ->
            ProblemContent(
                "Connection not verified",
                state.message,
                if (state.retryable) "Retry" else null,
                actions.retryStoredVerification,
                actions.forgetLocalConnection
            )

        is ConnectionUiState.RestoreProblem ->
            ProblemContent(
                "Library unavailable",
                state.message,
                "Retry",
                actions.retryStoredVerification,
                actions.forgetLocalConnection
            )

        is ConnectionUiState.AuthenticationRequired ->
            ProblemContent(
                "Repair connection",
                state.message,
                "Repair connection",
                actions.relinkLocalAccount,
                actions.forgetLocalConnection
            )

        is ConnectionUiState.LocalStorageProblem ->
            ProblemContent(
                "Local connection data unavailable",
                state.message,
                "Retry",
                actions.retryRestore,
                actions.forgetLocalConnection
            )

        else -> error("RecoveryContent requires a recovery state.")
    }
}

@Composable
private fun ConnectionFrame(content: @Composable () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
            Text(
                "Second Pass Reader",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Column(
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun ServerEntryContent(
    state: ConnectionUiState.ServerEntry,
    onUrlChanged: (String) -> Unit,
    onSuggestionSelected: (String) -> Unit,
    onVerify: () -> Unit,
    requestLanDiscoveryAccess: (() -> Unit)?
) {
    SectionTitle("Connect to a Library")
    requestLanDiscoveryAccess?.let { requestAccess ->
        OutlinedButton(onClick = requestAccess) {
            Text("Find nearby libraries")
        }
    }
    LibrarySuggestions(state.suggestions, state.serverUrl, onSuggestionSelected)
    val addressError = state.message?.takeIf { state.serverUrl.isNotBlank() }
    OutlinedTextField(
        value = state.serverUrl,
        onValueChange = onUrlChanged,
        modifier = Modifier.fillMaxWidth().testTag("library-address-field")
            .then(addressError?.let { Modifier.semantics { error(it) } } ?: Modifier),
        label = { Text("Library address") },
        placeholder = { Text("https://library.example") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        isError = addressError != null,
        supportingText = addressError?.let { message -> { Text(message) } }
    )
    ActionRow {
        Button(onClick = onVerify, enabled = state.serverUrl.isNotBlank()) {
            Text("Check address")
        }
    }
}

@Composable
private fun ServerConfirmedContent(
    state: ConnectionUiState.ServerConfirmed,
    onNameChanged: (String) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit
) {
    SectionTitle("Is this your Library?")
    ServerIdentityCard(state.server)
    OutlinedTextField(
        value = state.clientName,
        onValueChange = onNameChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Device name") },
        singleLine = true
    )
    ActionRow {
        OutlinedButton(onClick = onBack) { Text("Change address") }
        Button(onClick = onStart, enabled = state.clientName.isNotBlank()) { Text("Link device") }
    }
}

@Composable
private fun WaitingContent(state: ConnectionUiState.WaitingForApproval, onCancel: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    SectionTitle("Approve this device")
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Approval code", style = MaterialTheme.typography.labelLarge)
            SelectionContainer {
                Text(
                    state.code,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.displayLarge,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                "Open the approval page in ${state.serverName}.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                state.statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                ApprovalExpiryPresenter.label(state.expiresAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    ActionRow {
        OutlinedButton(onClick = onCancel) { Text("Cancel") }
        Button(onClick = {
            uriHandler.openUri(state.authorizeUrl)
        }) { Text("Open approval page") }
    }
}

@Composable
private fun ProblemContent(
    title: String,
    message: String,
    primaryLabel: String?,
    onPrimary: () -> Unit,
    onForget: (() -> Unit)? = null
) {
    SectionTitle(title, message)
    ActionRow {
        onForget?.let {
            OutlinedButton(onClick = it) { Text("Forget connection and local data") }
        }
        primaryLabel?.let { Button(onClick = onPrimary) { Text(it) } }
    }
}

@Composable
private fun BusyContent(message: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator()
        Text(message, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SectionTitle(title: String, detail: String? = null) {
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
    detail?.let {
        Text(
            it,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun ActionRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
        content = { content() }
    )
}
