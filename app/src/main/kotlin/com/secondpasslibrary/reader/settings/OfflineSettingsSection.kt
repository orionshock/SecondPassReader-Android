package com.secondpasslibrary.reader.settings

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import com.secondpasslibrary.reader.app.AppAvailability
import com.secondpasslibrary.reader.app.AppAvailabilityReason
import com.secondpasslibrary.reader.app.storage.AccountLocalDownload
import com.secondpasslibrary.reader.design.book.PublicBookCover
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
    onRemoveAll: () -> Unit,
    onClearBook: (String) -> Unit,
    onBookDetails: (String) -> Unit
) {
    var selectedBookId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingBookId by rememberSaveable { mutableStateOf<String?>(null) }
    var clearBookId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmAll by rememberSaveable { mutableStateOf(false) }
    OfflineAuthorityRow(availability, checkingConnection, onWorkOffline, onReconnect)
    DownloadedBooksCard(
        state = state,
        onRefresh = onRefresh,
        onSelect = { selectedBookId = it },
        onRemove = { pendingBookId = it },
        onRemoveAll = { confirmAll = true }
    )
    state.downloads.firstOrNull { it.bookId == selectedBookId }?.let { book ->
        OfflineBookDetailsDialog(
            book,
            onDismiss = { selectedBookId = null },
            onBookDetails = {
                selectedBookId = null
                onBookDetails(book.bookId)
            },
            onRemove = {
                selectedBookId = null
                pendingBookId = book.bookId
            },
            onClear = {
                selectedBookId = null
                clearBookId = book.bookId
            }
        )
    }
    DownloadConfirmations(
        state.downloads,
        pendingBookId,
        clearBookId,
        confirmAll,
        onDismissBook = { pendingBookId = null },
        onConfirmBook = {
            pendingBookId = null
            onRemove(it)
        },
        onDismissAll = { confirmAll = false },
        onConfirmAll = {
            confirmAll = false
            onRemoveAll()
        },
        onDismissClear = { clearBookId = null },
        onConfirmClear = {
            clearBookId = null
            onClearBook(it)
        }
    )
}

@Composable
private fun OfflineAuthorityRow(
    availability: AppAvailability,
    checkingConnection: Boolean,
    onWorkOffline: () -> Unit,
    onReconnect: () -> Unit
) {
    val forced = (availability as? AppAvailability.Offline)?.reason ==
        AppAvailabilityReason.USER_CHOICE
    val checking = checkingConnection
    val action: @Composable () -> Unit = {
        OutlinedButton(
            onClick = onReconnect,
            enabled = !checking,
            modifier = Modifier.testTag("check-connection")
        ) {
            Text(
                if (forced ||
                    availability is AppAvailability.Offline
                ) {
                    "Reconnect"
                } else {
                    "Check connection"
                }
            )
        }
    }
    val toggle: @Composable () -> Unit = {
        Row(
            modifier = Modifier.testTag("work-offline-switch")
                .toggleable(value = forced, enabled = !checking, role = Role.Switch) { enabled ->
                    if (enabled) onWorkOffline() else onReconnect()
                }.padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Work offline", style = MaterialTheme.typography.titleMedium)
            Switch(checked = forced, onCheckedChange = null, enabled = !checking)
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 480.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                action()
                toggle()
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                action()
                Spacer(Modifier.weight(1f))
                toggle()
            }
        }
    }
}

@Composable
private fun DownloadedBooksCard(
    state: SettingsDownloadsState,
    onRefresh: () -> Unit,
    onSelect: (String) -> Unit,
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            state.downloads.forEach { book ->
                DownloadRow(
                    book,
                    !state.busy,
                    onSelect = { onSelect(book.bookId) },
                    onRemove = { onRemove(book.bookId) }
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TextButton(onClick = onRemoveAll, enabled = !state.busy) {
                Text("Remove all downloads", color = MaterialTheme.colorScheme.error)
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
    clearBookId: String?,
    confirmAll: Boolean,
    onDismissBook: () -> Unit,
    onConfirmBook: (String) -> Unit,
    onDismissAll: () -> Unit,
    onConfirmAll: () -> Unit,
    onDismissClear: () -> Unit,
    onConfirmClear: (String) -> Unit
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
    downloads.firstOrNull { it.bookId == clearBookId }?.let { book ->
        DownloadRemovalDialog(
            title = "Clear offline data for this Book?",
            message = "The download, local progress, Marginalia, and changes waiting to sync " +
                "for ${book.title} will be removed from this device. Nothing is deleted " +
                "from Second Pass Library.",
            confirmLabel = "Clear offline data",
            onDismiss = onDismissClear,
            onConfirm = { onConfirmClear(book.bookId) }
        )
    }
}

@Composable
private fun DownloadRow(
    book: AccountLocalDownload,
    enabled: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth().testTag("download-${book.bookId}"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = "View offline Book details",
                    onClick = onSelect
                )
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PublicBookCover(
                reference = null,
                title = book.title,
                localCover = book.cover,
                contentDescription = null,
                modifier = Modifier.size(width = 34.dp, height = 48.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                book.author?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
            }
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
private fun OfflineBookDetailsDialog(
    book: AccountLocalDownload,
    onDismiss: () -> Unit,
    onBookDetails: () -> Unit,
    onRemove: () -> Unit,
    onClear: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PublicBookCover(
                        null,
                        book.title,
                        Modifier.size(48.dp, 68.dp),
                        contentDescription = null,
                        localCover = book.cover
                    )
                    Column {
                        Text(book.title, style = MaterialTheme.typography.titleLarge)
                        book.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
                OfflineBookLocalFacts(book)
                HorizontalDivider()
                TextButton(onClick = onRemove) {
                    Text("Remove download")
                }
                TextButton(onClick = onClear) {
                    Text("Clear offline data", color = MaterialTheme.colorScheme.error)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onBookDetails) { Text("Book details") }
                }
            }
        }
    }
}

@Composable
private fun OfflineBookLocalFacts(book: AccountLocalDownload) {
    val context = LocalContext.current
    Text("Downloaded EPUB · ${Formatter.formatShortFileSize(context, book.epubBytes)}")
    Text(if (book.cover != null) "Cover saved on this device" else "No cover saved")
    if (book.localSessionCount > 0) Text("Reading history on this device")
    if (book.pendingChangeCount > 0) {
        Text(
            if (book.pendingChangeCount == 1) {
                "1 Reader change waiting to sync"
            } else {
                "${book.pendingChangeCount} Reader changes waiting to sync"
            }
        )
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
