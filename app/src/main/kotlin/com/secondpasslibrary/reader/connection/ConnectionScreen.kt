package com.secondpasslibrary.reader.connection

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.design.components.InformationCard
import com.secondpasslibrary.reader.design.components.InformationDetail

@Composable
internal fun ConnectionScreen(state: ConnectionUiState, actions: ConnectionScreenActions) {
    ConnectionFrame {
        when (state) {
            ConnectionUiState.Restoring -> BusyContent("Restoring connection")

            is ConnectionUiState.ServerEntry ->
                ServerEntryContent(state, actions.updateServerUrl, actions.verifyServer)

            is ConnectionUiState.VerifyingServer -> BusyContent("Verifying ${state.serverUrl}")

            is ConnectionUiState.ServerConfirmed ->
                ServerConfirmedContent(
                    state,
                    actions.updateClientName,
                    actions.beginPairing,
                    actions.abandonPairing
                )

            is ConnectionUiState.StartingPairing ->
                BusyContent("Starting secure linking with ${state.server.name}")

            is ConnectionUiState.WaitingForApproval ->
                WaitingContent(state, actions.abandonPairing)

            is ConnectionUiState.CompletingPairing ->
                BusyContent("Securing the approved credential from ${state.serverName}")

            is ConnectionUiState.PersistenceRecovery,
            is ConnectionUiState.StoredCredentialProblem,
            is ConnectionUiState.RestoreProblem,
            is ConnectionUiState.AuthenticationRequired,
            is ConnectionUiState.LocalStorageProblem -> RecoveryContent(state, actions)

            is ConnectionUiState.TerminalPairingProblem ->
                ProblemContent(
                    "Linking stopped",
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
                "Credential secured",
                state.message,
                "Retry profile storage",
                actions.retryProfilePersistence,
                actions.forgetLocalConnection
            )

        is ConnectionUiState.StoredCredentialProblem ->
            ProblemContent(
                "Stored, not verified",
                state.message,
                if (state.retryable) "Retry verification" else null,
                actions.retryStoredVerification,
                actions.forgetLocalConnection
            )

        is ConnectionUiState.RestoreProblem ->
            ProblemContent(
                "Library temporarily unavailable",
                state.message,
                "Retry connection",
                actions.retryStoredVerification,
                actions.forgetLocalConnection
            )

        is ConnectionUiState.AuthenticationRequired ->
            ProblemContent(
                "Authentication required",
                state.message,
                "Link again",
                actions.relinkLocalAccount,
                actions.forgetLocalConnection
            )

        is ConnectionUiState.LocalStorageProblem ->
            ProblemContent(
                "Local connection storage unavailable",
                state.message,
                "Retry restore",
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
                .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().widthIn(max = 920.dp)) {
            Text(
                "SECOND PASS",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                "Reader",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 920.dp),
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
    onVerify: () -> Unit
) {
    SectionTitle(
        "Connect a library",
        "Enter any URL on your Second Pass Library server. The client will verify its public identity."
    )
    OutlinedTextField(
        value = state.serverUrl,
        onValueChange = onUrlChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Server URL") },
        placeholder = { Text("https://library.example") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        supportingText = state.message?.let { message -> { Text(message) } }
    )
    Button(onClick = onVerify, enabled = state.serverUrl.isNotBlank()) { Text("Verify server") }
}

@Composable
private fun ServerConfirmedContent(
    state: ConnectionUiState.ServerConfirmed,
    onNameChanged: (String) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit
) {
    SectionTitle(
        "Server verified",
        "Confirm the authoritative server identity and name this Android client."
    )
    ServerIdentityCard(state.server)
    OutlinedTextField(
        value = state.clientName,
        onValueChange = onNameChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Client name") },
        singleLine = true,
        supportingText = { Text("1–200 characters · type is fixed as second-pass-android-client") }
    )
    ActionRow {
        Button(onClick = onStart, enabled = state.clientName.isNotBlank()) { Text("Start linking") }
        OutlinedButton(onClick = onBack) { Text("Different server") }
    }
}

@Composable
private fun WaitingContent(state: ConnectionUiState.WaitingForApproval, onCancel: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    SectionTitle(
        "Approve this device",
        "Use the code below in ${state.server.name}. The code remains usable without opening the browser."
    )
    InformationCard("Approval code") {
        SelectionContainer {
            Text(
                state.request.code,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.displayMedium,
                fontFamily = FontFamily.Monospace
            )
        }
        InformationDetail("Status", state.statusText)
        InformationDetail("Expires", state.request.expiresAt)
    }
    ActionRow {
        Button(onClick = {
            uriHandler.openUri(state.request.authorizeUrl)
        }) { Text("Open approval page") }
        OutlinedButton(onClick = onCancel) { Text("Cancel") }
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
        primaryLabel?.let { Button(onClick = onPrimary) { Text(it) } }
        onForget?.let { OutlinedButton(onClick = it) { Text("Forget locally") } }
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
private fun SectionTitle(title: String, detail: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
    Text(
        detail,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyLarge
    )
}

@Composable
private fun ActionRow(content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), content = { content() })
}
