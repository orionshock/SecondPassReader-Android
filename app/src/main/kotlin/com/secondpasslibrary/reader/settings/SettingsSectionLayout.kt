package com.secondpasslibrary.reader.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsSectionLayout(
    selected: SettingsSection,
    onSelect: (SettingsSection) -> Unit,
    content: @Composable (SettingsSection) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 720.dp) {
            Row(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column(
                    modifier = Modifier.width(208.dp).selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SettingsSection.entries.forEach { section ->
                        SettingsSectionChoice(
                            section,
                            selected == section,
                            onSelect,
                            Modifier.fillMaxWidth()
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        content(selected)
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SettingsSection.entries.forEach { section ->
                        SettingsSectionChoice(section, selected == section, onSelect)
                    }
                }
                content(selected)
            }
        }
    }
}

@Composable
private fun SettingsSectionChoice(
    section: SettingsSection,
    isSelected: Boolean,
    onSelect: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.testTag("settings-section-${section.name.lowercase()}")
            .selectable(selected = isSelected, role = Role.Tab) { onSelect(section) },
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Text(
            section.label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            color = if (isSelected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
