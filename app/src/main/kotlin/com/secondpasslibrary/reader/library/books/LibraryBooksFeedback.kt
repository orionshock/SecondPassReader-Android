package com.secondpasslibrary.reader.library.books

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.library.LibraryFailure

@Composable
internal fun NextPageFooter(state: LibraryBooksState, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(64.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            state.nextPageLoading -> CircularProgressIndicator(
                Modifier.size(24.dp),
                strokeWidth = 2.dp
            )

            state.error?.phase == LibraryBooksLoadPhase.NEXT_PAGE ->
                OutlinedButton(onClick = onRetry) { Text("Could not load more - Retry") }
        }
    }
}

@Composable
internal fun ReplacementFeedback(state: LibraryBooksState, onRetry: () -> Unit) {
    when {
        state.initialLoading || state.refreshing -> LinearProgressIndicator(Modifier.fillMaxWidth())

        state.error?.phase != null && state.error.phase != LibraryBooksLoadPhase.NEXT_PAGE ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    state.error.failure.message(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium
                )
                OutlinedButton(onClick = onRetry) { Text("Retry") }
            }
    }
}

@Composable
internal fun EmptyLibrary(modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No books found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun OfflineDownloadedLibraryEmpty(modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("No downloaded books available offline.")
        Text(
            "Connect to your library to browse the full catalog.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
internal fun LibraryFailureContent(
    error: LibraryBooksLoadError,
    onRetry: () -> Unit,
    modifier: Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(error.failure.message(), color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = 10.dp)) {
            Text("Retry")
        }
    }
}

private fun LibraryFailure.message(): String = when (this) {
    LibraryFailure.UNREACHABLE -> "Library is currently unreachable."
    LibraryFailure.AUTHENTICATION_REJECTED -> "Library authentication was rejected."
    LibraryFailure.PROTOCOL_INVALID -> "The library returned an invalid response."
    LibraryFailure.OTHER -> "Books could not be loaded."
}
