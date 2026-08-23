package com.secondpasslibrary.reader.app.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.secondpasslibrary.reader.app.AppSessionAuthority

internal fun EntryProviderScope<NavKey>.registerConnectionRequiredEntries(
    authority: AppSessionAuthority,
    onRetryConnection: () -> Unit,
    onRelinkAccount: () -> Unit,
    onForgetAccount: () -> Unit,
    onOpenDrawer: () -> Unit
) {
    val content = @Composable {
        ConnectionRequiredDestination(
            authority,
            onRetryConnection,
            onRelinkAccount,
            onForgetAccount,
            onOpenDrawer
        )
    }
    entry(key = AppDestination.Library) { content() }
    entry(key = AppDestination.Shelves) { content() }
    entry(key = AppDestination.Marginalia) { content() }
    entry(key = AppDestination.Settings) { content() }
    entry<LibrarySearchRoute> { content() }
    entry<LibraryAuthorRoute> { content() }
    entry<LibrarySeriesRoute> { content() }
    entry<LibraryTagRoute> { content() }
    entry<BookDetailRoute> { content() }
    entry<BookMarginaliaRoute> { content() }
}

@Composable
private fun ConnectionRequiredDestination(
    authority: AppSessionAuthority,
    onRetryConnection: () -> Unit,
    onRelinkAccount: () -> Unit,
    onForgetAccount: () -> Unit,
    onOpenDrawer: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            when (authority) {
                AppSessionAuthority.Restoring -> "Reconnecting..."

                is AppSessionAuthority.AuthenticationRequired ->
                    "Link this account again to use this section."

                else -> "This section needs a connection."
            },
            style = MaterialTheme.typography.titleMedium
        )
        if (authority is AppSessionAuthority.TransientFailure) {
            Button(onClick = onRetryConnection) { Text("Retry connection") }
        }
        if (authority is AppSessionAuthority.AuthenticationRequired) {
            Button(onClick = onRelinkAccount) { Text("Link again") }
            Button(onClick = onForgetAccount) { Text("Forget") }
        }
        Button(onClick = onOpenDrawer) { Text("Open navigation") }
    }
}
