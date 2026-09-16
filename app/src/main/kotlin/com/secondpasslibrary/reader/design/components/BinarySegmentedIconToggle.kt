package com.secondpasslibrary.reader.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.design.icons.AppIconGraphic

@Composable
internal fun <T> BinarySegmentedIconToggle(
    selected: T,
    first: SegmentedIconOption<T>,
    second: SegmentedIconOption<T>,
    onSelected: (T) -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(Modifier.selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
            Segment(selected == first.value, first) { onSelected(first.value) }
            VerticalDivider(Modifier.size(width = 1.dp, height = 26.dp))
            Segment(selected == second.value, second) { onSelected(second.value) }
        }
    }
}

@Composable
private fun <T> Segment(selected: Boolean, option: SegmentedIconOption<T>, onClick: () -> Unit) {
    val background =
        if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }
    val tint =
        if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    Box(
        modifier =
            Modifier
                .size(48.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(background)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AppIconGraphic(option.icon, option.contentDescription, Modifier.size(22.dp), tint)
    }
}
