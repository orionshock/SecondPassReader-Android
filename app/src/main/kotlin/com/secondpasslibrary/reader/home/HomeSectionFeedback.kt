package com.secondpasslibrary.reader.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic

@Composable
internal fun HomeSectionLoading(label: String) {
    Row(
        modifier = Modifier.padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        Text("Loading $label...", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun HomeSectionEmpty(icon: AppIcon, message: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconGraphic(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun HomeSectionError(message: String, onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(message, color = MaterialTheme.colorScheme.error)
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
internal fun HomeSectionRefreshFeedback(
    refresh: HomeProjectionRefresh,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (refresh) {
        HomeProjectionRefresh.Refreshing ->
            LinearProgressIndicator(
                modifier = modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )

        is HomeProjectionRefresh.Failed ->
            Surface(
                modifier = modifier,
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (refresh.reason == HomeProjectionFailure.Unreachable) {
                        AppIconGraphic(
                            AppIcon.Offline,
                            "Offline",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        refresh.messageWithCache(),
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
            }

        HomeProjectionRefresh.Current,
        HomeProjectionRefresh.Idle -> Unit
    }
}

private fun HomeProjectionRefresh.Failed.messageWithCache(): String = when (reason) {
    HomeProjectionFailure.Unreachable -> "Offline — showing cached data"

    HomeProjectionFailure.AuthenticationRejected ->
        "Authorization was rejected — showing cached data"

    HomeProjectionFailure.ProtocolInvalid ->
        "Refresh returned invalid data — showing cached data"

    HomeProjectionFailure.Other -> "Refresh failed — showing cached data"
}
