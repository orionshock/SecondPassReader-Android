package com.secondpasslibrary.reader.connection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.connection.discovery.ConnectionLibrarySuggestion
import com.secondpasslibrary.reader.design.richtext.ServerRichText
import com.secondpasslibrary.reader.design.richtext.serverRichTextPlainText

@Composable
internal fun LibrarySuggestions(
    suggestions: List<ConnectionLibrarySuggestion>,
    selectedUrl: String,
    onSelected: (String) -> Unit
) {
    if (suggestions.isNotEmpty()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Nearby libraries",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            suggestions.forEach { suggestion ->
                LibrarySuggestionCard(suggestion, suggestion.url == selectedUrl, onSelected)
            }
        }
    }
}

@Composable
private fun LibrarySuggestionCard(
    suggestion: ConnectionLibrarySuggestion,
    isSelected: Boolean,
    onSelected: (String) -> Unit
) {
    val spokenLabel = listOf(
        suggestion.name,
        suggestion.url,
        serverRichTextPlainText(suggestion.description)
    ).joinToString(", ")
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Use this address") { onSelected(suggestion.url) }
            .clearAndSetSemantics {
                contentDescription = spokenLabel
                role = Role.Button
                selected = isSelected
                onClick(label = "Use this address") {
                    onSelected(suggestion.url)
                    true
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(
            1.dp,
            if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                suggestion.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                suggestion.url,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ServerRichText(
                suggestion.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                collapsedMaxLines = 3
            )
        }
    }
}
