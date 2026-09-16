package com.secondpasslibrary.reader.reader.appearance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    ReaderSegmentedControl(
        label = "Layout",
        options = ReaderLayoutMode.entries,
        selected = selected,
        displayName = ReaderLayoutMode::displayName,
        colors = colors,
        onSelected = onSelected
    )
}

@Composable
private fun ReaderThemeControl(
    selected: ReaderTheme,
    colors: ReaderAppearanceControlColors,
    onSelected: (ReaderTheme) -> Unit
) {
    ReaderSegmentedControl(
        label = "Theme",
        options = ReaderTheme.entries,
        selected = selected,
        displayName = ReaderTheme::displayName,
        colors = colors,
        onSelected = onSelected
    )
}

@Composable
private fun <T> ReaderSegmentedControl(
    label: String,
    options: List<T>,
    selected: T,
    displayName: (T) -> String,
    colors: ReaderAppearanceControlColors,
    onSelected: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = colors.secondaryContent, style = MaterialTheme.typography.labelMedium)
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            border = BorderStroke(1.dp, colors.secondaryContent.copy(alpha = 0.5f)),
            color = Color.Transparent
        ) {
            Row(Modifier.selectableGroup()) {
                options.forEach { option ->
                    ReaderSegment(
                        label = displayName(option),
                        isSelected = option == selected,
                        colors = colors,
                        onClick = { onSelected(option) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.ReaderSegment(
    label: String,
    isSelected: Boolean,
    colors: ReaderAppearanceControlColors,
    onClick: () -> Unit
) {
    Text(
        text = label,
        modifier = Modifier
            .weight(1f)
            .defaultMinSize(minHeight = 48.dp)
            .background(if (isSelected) colors.selectedBackground else Color.Transparent)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .wrapContentHeight(Alignment.CenterVertically)
            .padding(horizontal = 12.dp),
        color = if (isSelected) colors.content else colors.secondaryContent,
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        ),
        textAlign = TextAlign.Center
    )
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
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            border = BorderStroke(1.dp, colors.secondaryContent.copy(alpha = 0.5f)),
            color = Color.Transparent
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onValueChanged((value - step).coerceIn(valueRange)) },
                    enabled = value > valueRange.start,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = "Decrease $label" }
                ) {
                    Text("−", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = formatReaderScale(value),
                    modifier = Modifier.widthIn(min = 64.dp),
                    color = colors.content,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    textAlign = TextAlign.Center
                )
                IconButton(
                    onClick = { onValueChanged((value + step).coerceIn(valueRange)) },
                    enabled = value < valueRange.endInclusive,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = "Increase $label" }
                ) {
                    Text("+", style = MaterialTheme.typography.titleMedium)
                }
            }
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
        modifier =
            Modifier
                .fillMaxWidth()
                .toggleable(
                    value = appearance.publisherStylesEnabled,
                    role = Role.Switch,
                    onValueChange = {
                        onAppearanceChanged(appearance.copy(publisherStylesEnabled = it))
                    }
                )
                .semantics { contentDescription = "Publisher styles" },
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
            onCheckedChange = null
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
