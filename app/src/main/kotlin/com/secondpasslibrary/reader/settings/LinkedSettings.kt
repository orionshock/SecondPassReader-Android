package com.secondpasslibrary.reader.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.client.AuthenticatedContext
import com.secondpasslibrary.reader.connection.ConnectionLifecycleActionState
import com.secondpasslibrary.reader.connection.ConnectionLifecycleActions
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.design.components.InformationCard
import com.secondpasslibrary.reader.design.components.InformationDetail
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic

private enum class SettingsConfirmation { LOGOUT, FORGET }

@Composable
internal fun LinkedSettings(
    profile: ConnectionProfile,
    context: AuthenticatedContext?,
    status: SettingsConnectionStatus,
    lifecycleActionState: ConnectionLifecycleActionState,
    lifecycleActions: ConnectionLifecycleActions
) {
    val presentation = settingsPresentation(profile, context, status)
    var technicalDetailsExpanded by rememberSaveable { mutableStateOf(false) }
    var confirmation by rememberSaveable { mutableStateOf<SettingsConfirmation?>(null) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IdentitySections(presentation)
        ReaderSettingsStateHost()
        TechnicalDetailsSection(
            presentation.technicalDetails,
            technicalDetailsExpanded,
            onToggle = { technicalDetailsExpanded = !technicalDetailsExpanded }
        )
        ConnectionActionsSection(
            presentation.status,
            lifecycleActionState,
            lifecycleActions,
            onConfirmLogout = { confirmation = SettingsConfirmation.LOGOUT },
            onConfirmForget = { confirmation = SettingsConfirmation.FORGET }
        )
    }

    confirmation?.let { requested ->
        ConnectionConfirmationDialog(
            requested,
            onConfirm = {
                confirmation = null
                when (requested) {
                    SettingsConfirmation.LOGOUT -> lifecycleActions.logout()
                    SettingsConfirmation.FORGET -> lifecycleActions.forget()
                }
            },
            onDismiss = { confirmation = null }
        )
    }
}

@Composable
private fun IdentitySections(presentation: SettingsPresentation) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 900.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ConnectedLibrarySection(presentation, Modifier.weight(1f))
                AccountSection(presentation, Modifier.weight(1f))
                DeviceSection(presentation, Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ConnectedLibrarySection(presentation)
                AccountSection(presentation)
                DeviceSection(presentation)
            }
        }
    }
}

@Composable
private fun ConnectedLibrarySection(
    presentation: SettingsPresentation,
    modifier: Modifier = Modifier
) {
    InformationCard("Connected library", modifier, AppIcon.ConnectedLibrary) {
        InformationDetail("Library", presentation.libraryName)
        InformationDetail("Server", presentation.serverHost)
        InformationDetail("Status", presentation.status.label)
    }
}

@Composable
private fun AccountSection(presentation: SettingsPresentation, modifier: Modifier = Modifier) {
    InformationCard("You", modifier, AppIcon.Profile) {
        val user = presentation.user
        if (user == null) {
            Text(
                "Sign in to view current account details.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            InformationDetail("Name", user.displayName)
            InformationDetail("Username", user.username)
            InformationDetail("Role", user.role)
            user.email?.let { InformationDetail("Email", it) }
        }
    }
}

@Composable
private fun DeviceSection(presentation: SettingsPresentation, modifier: Modifier = Modifier) {
    InformationCard("This device", modifier, AppIcon.Success) {
        InformationDetail("App", "Second Pass Reader")
        InformationDetail("Device", presentation.clientName)
        InformationDetail("Status", presentation.status.label)
    }
}

@Composable
private fun TechnicalDetailsSection(
    details: List<SettingsTechnicalDetail>,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    InformationCard("Technical details", icon = AppIcon.Help) {
        TextButton(onClick = onToggle) {
            AppIconGraphic(if (expanded) AppIcon.Collapse else AppIcon.Expand, null)
            Text(if (expanded) "Hide technical details" else "Show technical details")
        }
        if (expanded) {
            details.forEach { detail ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        detail.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        detail.value,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionActionsSection(
    status: SettingsConnectionStatus,
    actionState: ConnectionLifecycleActionState,
    actions: ConnectionLifecycleActions,
    onConfirmLogout: () -> Unit,
    onConfirmForget: () -> Unit
) {
    val availability = status.actionAvailability()
    InformationCard("Connection actions", icon = AppIcon.Link) {
        if (availability.reconnect) {
            Text(
                "Sign back in to restore this connection. Local data is kept when you sign in " +
                    "as the same account.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = actions.reconnect) {
                AppIconGraphic(AppIcon.Link, null)
                Text("Sign back in", Modifier.padding(start = 8.dp))
            }
        }
        if (availability.retry) {
            OutlinedButton(onClick = actions.retryConnection) {
                AppIconGraphic(AppIcon.Offline, null)
                Text("Retry connection", Modifier.padding(start = 8.dp))
            }
        }
        if (availability.logout) {
            OutlinedButton(
                onClick = onConfirmLogout,
                enabled = actionState !is ConnectionLifecycleActionState.LoggingOut
            ) {
                if (actionState is ConnectionLifecycleActionState.LoggingOut) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Logging out…", Modifier.padding(start = 8.dp))
                } else {
                    AppIconGraphic(AppIcon.Logout, null)
                    Text("Log out", Modifier.padding(start = 8.dp))
                }
            }
        }
        if (actionState is ConnectionLifecycleActionState.LogoutFailed) {
            Text(
                actionState.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedButton(onClick = actions.logout) { Text("Retry log out") }
        }
        if (availability.forget) {
            TextButton(onClick = onConfirmForget) {
                AppIconGraphic(AppIcon.Delete, null, tint = MaterialTheme.colorScheme.error)
                Text(
                    "Forget this library",
                    Modifier.padding(start = 8.dp),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ConnectionConfirmationDialog(
    confirmation: SettingsConfirmation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val logout = confirmation == SettingsConfirmation.LOGOUT
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (logout) "Log out?" else "Forget this library?") },
        text = {
            Text(
                if (logout) {
                    "This will revoke this device's server session and remove its local account " +
                        "data from this device."
                } else {
                    "This removes this account/library connection and deletes locally stored " +
                        "data for it from this device. It does not delete your books, shelves, " +
                        "reading sessions, or annotations from the server."
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(if (logout) "Log out" else "Forget")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
