package com.secondpasslibrary.reader.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.secondpasslibrary.client.AuthenticatedContext
import com.secondpasslibrary.reader.app.AppAvailability
import com.secondpasslibrary.reader.app.storage.AccountLocalScope
import com.secondpasslibrary.reader.connection.ConnectionLifecycleActionState
import com.secondpasslibrary.reader.connection.ConnectionLifecycleActions
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.authenticatedConnectionIdentity

private enum class SettingsConfirmation { LOGOUT, FORGET }

@Composable
@Suppress("LongMethod") // The closed Settings section routing stays visible in one composition.
internal fun LinkedSettings(
    profile: ConnectionProfile,
    profileId: String,
    context: AuthenticatedContext?,
    status: SettingsConnectionStatus,
    availability: AppAvailability,
    checkingConnection: Boolean,
    onWorkOffline: () -> Unit,
    onReconnect: () -> Unit,
    lifecycleActionState: ConnectionLifecycleActionState,
    lifecycleActions: ConnectionLifecycleActions,
    onBookDetails: (String) -> Unit,
    downloadsViewModel: SettingsDownloadsViewModel = viewModel()
) {
    val presentation = settingsPresentation(profile, context, status)
    val account = AccountLocalScope.from(profile.serverId, profileId)
    val downloads by downloadsViewModel.state.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(SettingsSection.LIBRARY_ACCOUNT) }
    var technicalDetailsExpanded by rememberSaveable { mutableStateOf(false) }
    var confirmation by rememberSaveable { mutableStateOf<SettingsConfirmation?>(null) }
    LaunchedEffect(account) { downloadsViewModel.initialize(account) }
    RefreshDownloadsOnResume(selected, downloadsViewModel)

    SettingsSectionLayout(
        selected,
        onSelect = {
            selected = it
            if (it == SettingsSection.OFFLINE) downloadsViewModel.refresh()
        }
    ) { section ->
        when (section) {
            SettingsSection.LIBRARY_ACCOUNT -> {
                LibraryAccountSettings(presentation)
                ConnectionActionsSection(
                    presentation.status,
                    lifecycleActionState,
                    lifecycleActions,
                    onConfirmLogout = { confirmation = SettingsConfirmation.LOGOUT },
                    onConfirmForget = { confirmation = SettingsConfirmation.FORGET }
                )
            }

            SettingsSection.READER -> ReaderSettingsStateHost()

            SettingsSection.OFFLINE -> OfflineSettingsSection(
                availability = availability,
                checkingConnection = checkingConnection,
                onWorkOffline = onWorkOffline,
                onReconnect = onReconnect,
                state = downloads,
                onRefresh = downloadsViewModel::refresh,
                onRemove = downloadsViewModel::remove,
                onRemoveAll = downloadsViewModel::removeAll,
                onClearBook = {
                    downloadsViewModel.clearBook(
                        it,
                        profile.authenticatedConnectionIdentity(profileId)
                    )
                },
                onBookDetails = onBookDetails
            )

            SettingsSection.ADVANCED -> AdvancedSettingsSection(
                presentation.technicalDetails,
                technicalDetailsExpanded,
                onToggle = { technicalDetailsExpanded = !technicalDetailsExpanded }
            )
        }
    }

    confirmation?.let { requested ->
        ConnectionSettingsConfirmationDialog(
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
private fun RefreshDownloadsOnResume(
    selected: SettingsSection,
    downloadsViewModel: SettingsDownloadsViewModel
) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (selected == SettingsSection.OFFLINE) downloadsViewModel.refresh()
    }
}

@Composable
private fun ConnectionSettingsConfirmationDialog(
    confirmation: SettingsConfirmation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val logout = confirmation == SettingsConfirmation.LOGOUT
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (logout) "Log out?" else "Forget connection and local data?") },
        text = {
            Text(
                if (logout) {
                    "This ends this device’s session and removes its local account data."
                } else {
                    "This removes the connection and its local data from this device. " +
                        "Books, Shelves, Reading Sessions, and Marginalia remain in " +
                        "Second Pass Library."
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    if (logout) "Log out" else "Forget connection and local data",
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
