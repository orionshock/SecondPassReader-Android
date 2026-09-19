package com.secondpasslibrary.reader.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.design.components.InformationCard
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic

@Composable
internal fun AdvancedSettingsSection(
    details: List<SettingsTechnicalDetail>,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    InformationCard("Technical details", icon = AppIcon.Help) {
        TextButton(onClick = onToggle) {
            AppIconGraphic(if (expanded) AppIcon.Collapse else AppIcon.Expand, null)
            Text(if (expanded) "Hide technical details" else "Show technical details")
        }
        if (expanded) {
            details.forEach { detail ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        detail.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        detail.value,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
