package com.secondpasslibrary.reader.connection

internal sealed interface ConnectionLifecycleActionState {
    data object Idle : ConnectionLifecycleActionState

    data object LoggingOut : ConnectionLifecycleActionState

    data class LogoutFailed(val message: String) : ConnectionLifecycleActionState
}

internal data class ConnectionLifecycleActions(
    val reconnect: () -> Unit,
    val retryConnection: () -> Unit,
    val logout: () -> Unit,
    val forget: () -> Unit
)
