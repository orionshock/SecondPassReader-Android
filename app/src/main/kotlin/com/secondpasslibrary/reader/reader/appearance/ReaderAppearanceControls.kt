package com.secondpasslibrary.reader.reader.appearance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.util.Locale

private data class ReaderAppearanceControlColors(
    val content: Color,
    val secondaryContent: Color,
    val selectedBackground: Color
)

@Composable
internal fun ReaderAppearanceControls(
    appearance: ReaderAppearance,
    contentColor: Color,
    secondaryContentColor: Color,
    selectedBackgroundColor: Color,
    onAppearanceChanged: (ReaderAppearance) -> Unit
) {
    val colors = ReaderAppearanceControlColors(
        contentColor,
        secondaryContentColor,
        selectedBackgroundColor
    )
    ReaderThemeControl(appearance.theme, colors) { theme ->
        onAppearanceChanged(appearance.copy(theme = theme))
    }
    ReaderLayoutModeControl(appearance.layoutMode, colors) { layoutMode ->
        onAppearanceChanged(appearance.copy(layoutMode = layoutMode))
    }
    ReaderStepControl(
        label = "Font size",
        value = appearance.fontScale,
        valueRange = ReaderAppearance.FONT_SCALE_RANGE,
        step = ReaderAppearance.FONT_SCALE_STEP,
        colors = colors
    ) { onAppearanceChanged(appearance.copy(fontScale = it)) }
    ReaderStepControl(
        label = "Line height",
        value = appearance.lineHeight,
        valueRange = ReaderAppearance.LINE_HEIGHT_RANGE,
        step = ReaderAppearance.LINE_HEIGHT_STEP,
        colors = colors
    ) { onAppearanceChanged(appearance.copy(lineHeight = it)) }
    PublisherStylesControl(appearance, colors, onAppearanceChanged)
}

@Composable
private fun ReaderLayoutModeControl(
    selected: ReaderLayoutMode,
    colors: ReaderAppearanceControlColors,
    onSelected: (ReaderLayoutMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Layout",
            color = colors.secondaryContent,
            style = MaterialTheme.typography.labelMedium
        )
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            border = BorderStroke(1.dp, colors.secondaryContent.copy(alpha = 0.5f)),
            color = Color.Transparent
        ) {
            Row(Modifier.selectableGroup()) {
                ReaderLayoutMode.entries.forEach { mode ->
                    val isSelected = mode == selected
                    Text(
                        text = mode.displayName(),
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (isSelected) colors.selectedBackground else Color.Transparent
                            )
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { onSelected(mode) }
                            )
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        color = if (isSelected) colors.content else colors.secondaryContent,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderThemeControl(
    selected: ReaderTheme,
    colors: ReaderAppearanceControlColors,
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
                    val selectedTheme = theme == selected
                    Text(
                        text = theme.displayName(),
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (selectedTheme) colors.selectedBackground else Color.Transparent
                            )
                            .selectable(
                                selected = selectedTheme,
                                role = Role.RadioButton,
                                onClick = { onSelected(theme) }
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        color = if (selectedTheme) colors.content else colors.secondaryContent,
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
    colors: ReaderAppearanceControlColors,
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
                contentPadding = PaddingValues(horizontal = 14.dp)
            ) { Text("−") }
            Text(formatReaderScale(value), style = MaterialTheme.typography.labelLarge)
            OutlinedButton(
                onClick = { onValueChanged((value + step).coerceIn(valueRange)) },
                enabled = value < valueRange.endInclusive,
                modifier = Modifier.semantics { contentDescription = "Increase $label" },
                contentPadding = PaddingValues(horizontal = 14.dp)
            ) { Text("+") }
        }
    }
}

@Composable
private fun PublisherStylesControl(
    appearance: ReaderAppearance,
    colors: ReaderAppearanceControlColors,
    onAppearanceChanged: (ReaderAppearance) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Publisher styles",
            color = colors.secondaryContent,
            style = MaterialTheme.typography.bodyMedium
        )
        Switch(
            checked = appearance.publisherStylesEnabled,
            modifier = Modifier.semantics { contentDescription = "Publisher styles" },
            onCheckedChange = {
                onAppearanceChanged(appearance.copy(publisherStylesEnabled = it))
            }
        )
    }
}

internal fun ReaderTheme.displayName(): String =
    name.lowercase().replaceFirstChar { it.titlecase(Locale.US) }

internal fun ReaderLayoutMode.displayName(): String = when (this) {
    ReaderLayoutMode.SINGLE_COLUMN -> "Single"
    ReaderLayoutMode.AUTO -> "Auto"
    ReaderLayoutMode.TWO_COLUMN -> "Two-column"
}

internal fun formatReaderScale(value: Double): String = String.format(Locale.US, "%.1f×", value)
