package com.secondpasslibrary.reader.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.secondpasslibrary.reader.design.components.InformationCard
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearance
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearanceControls

@Composable
internal fun ReaderSettingsStateHost(viewModel: ReaderSettingsViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ReaderSettingsSection(
        state,
        onAppearanceChanged = viewModel::updateAppearance,
        onAutoShowPreviousChanged = viewModel::setAutoShowPreviousMarginalia
    )
}

@Composable
internal fun ReaderSettingsSection(
    state: ReaderSettingsState,
    onAppearanceChanged: (ReaderAppearance) -> Unit,
    onAutoShowPreviousChanged: (Boolean) -> Unit
) {
    InformationCard("Reader", icon = AppIcon.Book) {
        Text("Reading appearance", style = MaterialTheme.typography.titleSmall)
        ReaderAppearanceControls(
            state.appearance,
            MaterialTheme.colorScheme.onSurface,
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.secondaryContainer,
            onAppearanceChanged
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text("Marginalia", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = state.autoShowPreviousMarginalia,
                        role = Role.Switch,
                        onValueChange = onAutoShowPreviousChanged
                    )
                    .semantics {
                        contentDescription = "Show previous marginalia automatically"
                    },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Show previous marginalia automatically",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Switch(
                checked = state.autoShowPreviousMarginalia,
                onCheckedChange = null
            )
        }
    }
}
