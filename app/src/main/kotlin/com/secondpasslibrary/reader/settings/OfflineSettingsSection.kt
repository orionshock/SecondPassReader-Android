package com.secondpasslibrary.reader.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.app.AppAvailability
import com.secondpasslibrary.reader.app.AppAvailabilityReason
import com.secondpasslibrary.reader.app.storage.AccountLocalDownload
import com.secondpasslibrary.reader.design.components.InformationCard
import com.secondpasslibrary.reader.design.icons.AppIcon

@Composable
internal fun OfflineSettingsSection(
    availability: AppAvailability,
    checkingConnection: Boolean,
    onWorkOffline: () -> Unit,
    onReconnect: () -> Unit,
    state: SettingsDownloadsState,
    onRefresh: () -> Unit,
    onRemove: (String) -> Unit,
    onRemoveAll: () -> Unit
) {
    var managing by rememberSaveable { mutableStateOf(false) }
    var pendingBookId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmAll by rememberSaveable { mutableStateOf(false) }
    OfflineAuthorityCard(availability, checkingConnection, onWorkOffline, onReconnect)
    DownloadedBooksCard(
        state = state,
        managing = managing,
        onManage = { managing = !managing },
        onRefresh = onRefresh,
        onRemove = { pendingBookId = it },
        onRemoveAll = { confirmAll = true }
    )
    DownloadConfirmations(
        state.downloads,
        pendingBookId,
        confirmAll,
        onDismissBook = { pendingBookId = null },
        onConfirmBook = {
            pendingBookId = null
            onRemove(it)
        },
        onDismissAll = { confirmAll = false },
        onConfirmAll = {
            confirmAll = false
            managing = false
            onRemoveAll()
        }
    )
}

@Composable
private fun OfflineAuthorityCard(
    availability: AppAvailability,
    checkingConnection: Boolean,
    onWorkOffline: () -> Unit,
    onReconnect: () -> Unit
) {
    val forced = (availability as? AppAvailability.Offline)?.reason ==
        AppAvailabilityReason.USER_CHOICE
    val checking = checkingConnection
    InformationCard("Connection") {
        Row(
            modifier = Modifier.fillMaxWidth().testTag("work-offline-switch")
                .toggleable(value = forced, enabled = !checking, role = Role.Switch) { enabled ->
                    if (enabled) onWorkOffline() else onReconnect()
                },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Work offline", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        forced -> "Working offline"
                        checking -> "Checking connection"
                        availability is AppAvailability.Offline -> "Library unavailable"
                        else -> "Connected to Library"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = forced,
                onCheckedChange = null,
                enabled = !checking
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(
                onClick = onReconnect,
                enabled = !checking,
                modifier = Modifier.testTag("check-connection")
            ) {
                Text(if (forced) "Reconnect" else "Check connection")
            }
        }
    }
}

@Composable
private fun DownloadedBooksCard(
    state: SettingsDownloadsState,
    managing: Boolean,
    onManage: () -> Unit,
    onRefresh: () -> Unit,
    onRemove: (String) -> Unit,
    onRemoveAll: () -> Unit
) {
    InformationCard("Downloaded Books", icon = AppIcon.Book) {
        DownloadSummary(state)
        if (state.error) {
            Text("Couldn’t update downloads.", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRefresh, enabled = !state.busy) { Text("Retry") }
        }
        if (state.busy) Text("Removing downloads…")
        if (!state.loading && state.downloads.isNotEmpty()) {
            OutlinedButton(onClick = onManage, enabled = !state.busy) {
                Text(if (managing) "Done" else "Manage downloads")
            }
            if (managing) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                state.downloads.forEach { book ->
                    DownloadRow(book, !state.busy) { onRemove(book.bookId) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TextButton(onClick = onRemoveAll, enabled = !state.busy) {
                    Text("Remove all downloads", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun DownloadSummary(state: SettingsDownloadsState) {
    if (state.loading) {
        CircularProgressIndicator()
        return
    }
    if (state.error && state.downloads.isEmpty()) return
    val count = state.downloads.size
    Text(
        when (count) {
            0 -> "No downloaded Books"
            1 -> "1 downloaded Book"
            else -> "$count downloaded Books"
        },
        style = MaterialTheme.typography.titleMedium
    )
    if (count > 0) {
        val sizeLabel = Formatter.formatShortFileSize(LocalContext.current, state.totalBytes)
        Text(
            "$sizeLabel on this device",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DownloadConfirmations(
    downloads: List<AccountLocalDownload>,
    pendingBookId: String?,
    confirmAll: Boolean,
    onDismissBook: () -> Unit,
    onConfirmBook: (String) -> Unit,
    onDismissAll: () -> Unit,
    onConfirmAll: () -> Unit
) {
    val pendingBook = downloads.firstOrNull { it.bookId == pendingBookId }
    if (pendingBook != null) {
        DownloadRemovalDialog(
            title = "Remove download?",
            message = "Remove ${pendingBook.title} from this device? Your reading progress " +
                "and notes are kept.",
            confirmLabel = "Remove download",
            onDismiss = onDismissBook,
            onConfirm = { onConfirmBook(pendingBook.bookId) }
        )
    }
    if (confirmAll) {
        DownloadRemovalDialog(
            title = "Remove all downloads?",
            message = "Remove all downloaded Books from this device? Your reading progress, " +
                "notes, and Library connection are kept.",
            confirmLabel = "Remove downloads",
            onDismiss = onDismissAll,
            onConfirm = onConfirmAll
        )
    }
}

@Composable
private fun DownloadRow(book: AccountLocalDownload, enabled: Boolean, onRemove: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth().testTag("download-${book.bookId}"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(book.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                Formatter.formatShortFileSize(context, book.sizeBytes),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        TextButton(
            onClick = onRemove,
            enabled = enabled,
            modifier = Modifier.semantics {
                contentDescription = "Remove download of ${book.title}"
            }
        ) {
            Text("Remove download", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun DownloadRemovalDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm-download-removal")
            ) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
