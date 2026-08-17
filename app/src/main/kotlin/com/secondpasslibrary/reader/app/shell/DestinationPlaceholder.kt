package com.secondpasslibrary.reader.app.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.design.icons.AppIconGraphic

@Composable
internal fun DestinationPlaceholder(destination: AppDestination) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIconGraphic(
            icon = destination.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Text(destination.label, style = MaterialTheme.typography.headlineMedium)
        Text(
            "This destination is established; feature work is intentionally deferred.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
