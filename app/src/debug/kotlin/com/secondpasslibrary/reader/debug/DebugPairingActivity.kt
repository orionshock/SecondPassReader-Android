package com.secondpasslibrary.reader.debug

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secondpasslibrary.reader.app.MainActivity
import com.secondpasslibrary.reader.app.SecondPassApp
import com.secondpasslibrary.reader.app.readerActivityRestorationBootstrap
import com.secondpasslibrary.reader.connection.ConnectionUiState
import com.secondpasslibrary.reader.connection.ConnectionViewModel
import com.secondpasslibrary.reader.design.SecondPassTheme
import com.secondpasslibrary.reader.reader.lifecycle.restoreActivity
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Debug-only entry point that drives the real SPL pairing workflow from exact intent extras. */
@AndroidEntryPoint
class DebugPairingActivity : FragmentActivity() {
    private val connectionViewModel by viewModels<ConnectionViewModel>()
    private lateinit var statusFile: File

    override fun onCreate(savedInstanceState: Bundle?) {
        applicationContext.readerActivityRestorationBootstrap().restoreActivity(this) {
            super.onCreate(savedInstanceState)
        }
        val operation = intent.getStringExtra(EXTRA_OPERATION) ?: OPERATION_PAIR
        val serverUrl = intent.getStringExtra(EXTRA_SERVER_URL).orEmpty()
        require(operation == OPERATION_LOGOUT || serverUrl.isNotBlank()) {
            "Debug pairing requires $EXTRA_SERVER_URL."
        }
        val clientName = intent.getStringExtra(EXTRA_CLIENT_NAME)?.trim().orEmpty()
            .ifEmpty { defaultClientName() }
        statusFile = File(filesDir, STATUS_FILE_NAME).also { it.delete() }

        enableEdgeToEdge()
        setContent {
            SecondPassTheme {
                val state by connectionViewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(state) {
                    advanceConnection(state, operation, serverUrl, clientName)
                }
                SecondPassApp(connectionViewModel = connectionViewModel)
            }
        }
    }

    private fun advanceConnection(
        state: ConnectionUiState,
        operation: String,
        serverUrl: String,
        clientName: String
    ) {
        if (operation == OPERATION_LOGOUT) {
            advanceLogout(state)
            return
        }
        when (state) {
            is ConnectionUiState.ServerEntry -> {
                if (state.serverUrl == serverUrl && state.message != null) {
                    writeStatus("failed", message = state.message)
                    return
                }
                connectionViewModel.screenActions.updateServerUrl(serverUrl)
                connectionViewModel.screenActions.verifyServer()
                writeStatus("verifying")
            }

            is ConnectionUiState.ServerConfirmed -> {
                connectionViewModel.screenActions.updateClientName(clientName)
                connectionViewModel.screenActions.beginPairing()
                writeStatus("starting")
            }

            is ConnectionUiState.WaitingForApproval ->
                writeStatus(
                    status = "waiting",
                    code = state.request.code,
                    clientName = state.clientName
                )

            is ConnectionUiState.Linked -> {
                writeStatus("linked")
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                finish()
            }

            is ConnectionUiState.AuthenticationRequired -> {
                connectionViewModel.screenActions.relinkLocalAccount()
                writeStatus("verifying")
            }

            is ConnectionUiState.TerminalPairingProblem ->
                writeStatus("failed", message = state.message)

            is ConnectionUiState.LocalStorageProblem ->
                writeStatus("failed", message = state.message)

            else -> Unit
        }
    }

    private fun advanceLogout(state: ConnectionUiState) {
        when (state) {
            is ConnectionUiState.Linked -> {
                writeStatus("logging-out")
                connectionViewModel.lifecycleActions.logout()
            }

            is ConnectionUiState.ServerEntry -> {
                writeStatus("logged-out")
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                finish()
            }

            is ConnectionUiState.LocalStorageProblem ->
                writeStatus("failed", message = state.message)

            else -> Unit
        }
    }

    private fun writeStatus(
        status: String,
        code: String? = null,
        clientName: String? = null,
        message: String? = null
    ) {
        statusFile.writeText(
            buildString {
                appendLine("status=${status.toPropertyValue()}")
                code?.let { appendLine("code=${it.toPropertyValue()}") }
                clientName?.let { appendLine("clientName=${it.toPropertyValue()}") }
                message?.let { appendLine("message=${it.toPropertyValue()}") }
            }
        )
    }

    private fun String.toPropertyValue(): String =
        replace("\\", "\\\\").replace("\r", " ").replace("\n", " ")

    private fun defaultClientName(): String {
        val date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
        val suffix = UUID.randomUUID().toString().take(4).uppercase()
        return "Second Pass Android debug $date-$suffix"
    }

    internal companion object {
        const val EXTRA_SERVER_URL = "debug.server_url"
        const val EXTRA_CLIENT_NAME = "debug.client_name"
        const val EXTRA_OPERATION = "debug.operation"
        const val OPERATION_PAIR = "pair"
        const val OPERATION_LOGOUT = "logout"
        const val STATUS_FILE_NAME = "debug-pairing-status.properties"
    }
}
