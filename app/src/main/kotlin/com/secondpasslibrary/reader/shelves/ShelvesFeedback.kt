package com.secondpasslibrary.reader.shelves

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
import com.secondpasslibrary.reader.shelves.collection.ShelfCollectionState

@Composable
internal fun ShelvesNextPageFooter(
    loading: Boolean,
    error: ShelvesLoadError?,
    onRetry: () -> Unit
) {
    Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
        when {
            loading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)

            error?.phase == ShelvesLoadPhase.NEXT_PAGE ->
                OutlinedButton(onClick = onRetry) { Text("Retry loading more Shelves") }
        }
    }
}

@Composable
internal fun ShelvesLoading(modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
internal fun ShelvesEmpty(collection: ShelvesCollection, modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(collection.emptyLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ShelvesReplacementFeedback(state: ShelfCollectionState, onRetry: () -> Unit) {
    when {
        state.shelves.isNotEmpty() && state.refreshing ->
            LinearProgressIndicator(Modifier.fillMaxWidth())

        state.shelves.isNotEmpty() && state.error?.phase == ShelvesLoadPhase.INITIAL ->
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
internal fun ShelvesFailure(error: ShelvesLoadError, onRetry: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(error.failure.message(), color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = 10.dp)) {
            Text("Retry")
        }
    }
}

internal fun ShelvesFailure.message(): String = when (this) {
    ShelvesFailure.UNREACHABLE -> "Couldn’t reach the Library. Check your connection and retry."

    ShelvesFailure.AUTHENTICATION_REJECTED ->
        "Your connection is no longer authorized. Repair it in Settings."

    ShelvesFailure.PROTOCOL_INVALID ->
        "Couldn’t read the Library response. Retry or repair the connection in Settings."

    ShelvesFailure.OTHER -> "Couldn’t load Shelves. Retry."
}

private val ShelvesCollection.emptyLabel: String
    get() = when (this) {
        ShelvesCollection.PERSONAL -> "No personal shelves yet."
        ShelvesCollection.SHARED -> "No shelves shared with you."
        ShelvesCollection.GROUP -> "No group shelves available."
    }
