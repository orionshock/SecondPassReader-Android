package com.secondpasslibrary.reader.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.reader.domain.ReaderAppearance
import com.secondpasslibrary.reader.reader.domain.ReaderTheme
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ReaderAppearancePanel(
    appearance: ReaderAppearance,
    onAppearanceChanged: (ReaderAppearance) -> Unit,
    onDismissRequest: () -> Unit
) {
    val colors = appearance.theme.chromeColors()
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.panelBackground,
        contentColor = colors.content
    ) {
        ReaderAppearancePanelContent(appearance, colors, onAppearanceChanged)
    }
}

@Composable
private fun ReaderAppearancePanelContent(
    appearance: ReaderAppearance,
    colors: ReaderChromeColors,
    onAppearanceChanged: (ReaderAppearance) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Reading appearance", style = MaterialTheme.typography.titleMedium)
        ReaderThemeControl(appearance.theme, colors) { theme ->
            onAppearanceChanged(appearance.copy(theme = theme))
        }
        ReaderTypographyControls(appearance, colors, onAppearanceChanged)
        PublisherStylesControl(appearance, onAppearanceChanged)
    }
}

@Composable
private fun ReaderTypographyControls(
    appearance: ReaderAppearance,
    colors: ReaderChromeColors,
    onAppearanceChanged: (ReaderAppearance) -> Unit
) {
    ReaderStepControl(
        label = "Font size",
        value = appearance.fontScale,
        valueRange = ReaderAppearance.FONT_SCALE_RANGE,
        step = ReaderAppearance.FONT_SCALE_STEP,
        colors = colors
    ) { fontScale -> onAppearanceChanged(appearance.copy(fontScale = fontScale)) }
    ReaderStepControl(
        label = "Line height",
        value = appearance.lineHeight,
        valueRange = ReaderAppearance.LINE_HEIGHT_RANGE,
        step = ReaderAppearance.LINE_HEIGHT_STEP,
        colors = colors
    ) { lineHeight -> onAppearanceChanged(appearance.copy(lineHeight = lineHeight)) }
}

@Composable
private fun PublisherStylesControl(
    appearance: ReaderAppearance,
    onAppearanceChanged: (ReaderAppearance) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Publisher styles", style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = appearance.publisherStylesEnabled,
            modifier = Modifier.semantics { contentDescription = "Publisher styles" },
            onCheckedChange = {
                onAppearanceChanged(appearance.copy(publisherStylesEnabled = it))
            }
        )
    }
}

@Composable
private fun ReaderThemeControl(
    selected: ReaderTheme,
    colors: ReaderChromeColors,
    onSelected: (ReaderTheme) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Theme", color = colors.secondaryContent, style = MaterialTheme.typography.labelMedium)
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            border = BorderStroke(1.dp, colors.secondaryContent.copy(alpha = 0.5f)),
            color = Color.Transparent
        ) {
            Row(Modifier.selectableGroup()) {
                ReaderTheme.entries.forEach { theme ->
                    val isSelected = theme == selected
                    Text(
                        text = theme.displayName(),
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (isSelected) colors.selectedBackground else Color.Transparent
                            )
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { onSelected(theme) }
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        color = if (isSelected) colors.content else colors.secondaryContent,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderStepControl(
    label: String,
    value: Double,
    valueRange: ClosedFloatingPointRange<Double>,
    step: Double,
    colors: ReaderChromeColors,
    onValueChanged: (Double) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = colors.secondaryContent, style = MaterialTheme.typography.bodyMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { onValueChanged((value - step).coerceIn(valueRange)) },
                enabled = value > valueRange.start,
                modifier = Modifier.semantics { contentDescription = "Decrease $label" },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 14.dp
                )
            ) { Text("−") }
            Text(formatScale(value), style = MaterialTheme.typography.labelLarge)
            OutlinedButton(
                onClick = { onValueChanged((value + step).coerceIn(valueRange)) },
                enabled = value < valueRange.endInclusive,
                modifier = Modifier.semantics { contentDescription = "Increase $label" },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 14.dp
                )
            ) { Text("+") }
        }
    }
}

private fun ReaderTheme.displayName(): String =
    name.lowercase().replaceFirstChar { it.titlecase(Locale.US) }

private fun formatScale(value: Double): String = String.format(Locale.US, "%.1f×", value)
