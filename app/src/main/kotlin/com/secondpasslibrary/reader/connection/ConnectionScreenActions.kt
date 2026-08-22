package com.secondpasslibrary.reader.connection

internal data class ConnectionScreenActions(
    val updateServerUrl: (String) -> Unit,
    val verifyServer: () -> Unit,
    val updateClientName: (String) -> Unit,
    val beginPairing: () -> Unit,
    val abandonPairing: () -> Unit,
    val retryProfilePersistence: () -> Unit,
    val retryStoredVerification: () -> Unit,
    val retryRestore: () -> Unit,
    val forgetLocalConnection: () -> Unit
)
