package com.secondpasslibrary.reader.shelves

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
                OutlinedButton(onClick = onRetry) { Text("Could not load more - Retry") }
        }
    }
}

@Composable
internal fun ShelvesLoading(modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
internal fun ShelvesEmpty(modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No shelves in this collection.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    ShelvesFailure.UNREACHABLE -> "The library is currently unreachable."
    ShelvesFailure.AUTHENTICATION_REJECTED -> "Library authentication was rejected."
    ShelvesFailure.PROTOCOL_INVALID -> "The library returned an invalid response."
    ShelvesFailure.OTHER -> "Shelves could not be loaded."
}
