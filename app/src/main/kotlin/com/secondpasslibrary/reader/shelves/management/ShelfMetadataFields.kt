package com.secondpasslibrary.reader.shelves.management

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.client.ShelfVisibility

@Composable
internal fun ShelfMetadataFields(
    name: String,
    description: String,
    visibility: ShelfVisibility,
    enabled: Boolean,
    nameError: String?,
    descriptionError: String?,
    visibilityError: String?,
    onNameChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onVisibilityChanged: (ShelfVisibility) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChanged,
            modifier =
                Modifier.fillMaxWidth().then(
                    nameError?.let { message ->
                        Modifier.semantics { error(message) }
                    } ?: Modifier
                ),
            label = { Text("Name") },
            supportingText = nameError?.let { { Text(it) } },
            isError = nameError != null,
            singleLine = true,
            enabled = enabled
        )
        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChanged,
            modifier =
                Modifier.fillMaxWidth().then(
                    descriptionError?.let { message ->
                        Modifier.semantics { error(message) }
                    } ?: Modifier
                ),
            label = { Text("Shelf Description (optional)") },
            supportingText = descriptionError?.let { { Text(it) } },
            isError = descriptionError != null,
            minLines = 2,
            maxLines = 4,
            enabled = enabled
        )
        VisibilityChoices(
            visibility,
            enabled,
            visibilityError,
            onVisibilityChanged
        )
    }
}

@Composable
private fun VisibilityChoices(
    visibility: ShelfVisibility,
    enabled: Boolean,
    errorMessage: String?,
    onVisibilityChanged: (ShelfVisibility) -> Unit
) {
    Column(
        modifier =
            Modifier.then(
                errorMessage?.let { message -> Modifier.semantics { error(message) } } ?: Modifier
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        VisibilityChoice(
            "Private",
            "Visible only to you",
            ShelfVisibility.PRIVATE,
            visibility,
            enabled,
            onVisibilityChanged
        )
        VisibilityChoice(
            "Listed",
            "May be visible to readers who can see its books",
            ShelfVisibility.LISTED,
            visibility,
            enabled,
            onVisibilityChanged
        )
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun VisibilityChoice(
    label: String,
    explanation: String,
    value: ShelfVisibility,
    selected: ShelfVisibility,
    enabled: Boolean,
    onSelected: (ShelfVisibility) -> Unit
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .selectable(
                    selected = selected == value,
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = { onSelected(value) }
                )
                .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected == value,
            onClick = null,
            enabled = enabled
        )
        Column(Modifier.padding(start = 6.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                explanation,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
