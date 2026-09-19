package com.secondpasslibrary.reader.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.connection.ConnectionLifecycleActionState
import com.secondpasslibrary.reader.connection.ConnectionLifecycleActions
import com.secondpasslibrary.reader.design.components.InformationCard
import com.secondpasslibrary.reader.design.components.InformationDetail
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic
import com.secondpasslibrary.reader.design.richtext.ServerRichText

@Composable
internal fun LibraryAccountSettings(presentation: SettingsPresentation) {
    InformationCard("Library", icon = AppIcon.ConnectedLibrary) {
        Text(presentation.libraryName, style = MaterialTheme.typography.titleLarge)
        InformationDetail("Address", presentation.serverHost)
        InformationDetail("Status", presentation.status.label)
        presentation.serverDescription?.let { LabeledRichText("Description", it) }
        presentation.publicGroup?.description?.let {
            LabeledRichText(presentation.publicGroup.name, it)
        }
        presentation.serverBannerMessage?.let { LabeledRichText("Message", it) }
    }
    InformationCard("Account & device", icon = AppIcon.Profile) {
        presentation.user?.let { user ->
            InformationDetail("Name", user.displayName)
            InformationDetail("Username", user.username)
            InformationDetail("Role", user.role)
            user.email?.let { InformationDetail("Email", it) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        InformationDetail("Device", presentation.clientName)
    }
}

@Composable
private fun LabeledRichText(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium
        )
        ServerRichText(
            value = value,
            style = MaterialTheme.typography.bodyLarge,
            collapsedMaxLines = 6,
            expandOverflow = true,
            moreLabel = "Show more",
            lessLabel = "Show less"
        )
    }
}

@Composable
internal fun ConnectionActionsSection(
    status: SettingsConnectionStatus,
    actionState: ConnectionLifecycleActionState,
    actions: ConnectionLifecycleActions,
    onConfirmLogout: () -> Unit,
    onConfirmForget: () -> Unit
) {
    val availability = status.actionAvailability()
    InformationCard("Connection", icon = AppIcon.Link) {
        if (availability.reconnect) {
            Text(
                "Link this account again to repair the connection. Local data is kept if you " +
                    "use the same account.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = actions.reconnect) {
                AppIconGraphic(AppIcon.Link, null)
                Text("Repair connection", Modifier.padding(start = 8.dp))
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
        if (availability.forget) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TextButton(onClick = onConfirmForget) {
                AppIconGraphic(AppIcon.Delete, null, tint = MaterialTheme.colorScheme.error)
                Text(
                    "Forget connection and local data",
                    Modifier.padding(start = 8.dp),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
