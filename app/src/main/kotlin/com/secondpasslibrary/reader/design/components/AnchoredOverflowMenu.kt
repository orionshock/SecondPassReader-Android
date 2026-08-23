package com.secondpasslibrary.reader.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic

@Composable
internal fun <T> AnchoredOverflowMenu(
    items: List<T>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "More actions"
) {
    if (items.isEmpty()) return
    Box(modifier) {
        IconButton(
            onClick = { onExpandedChange(true) },
            modifier = Modifier.size(48.dp)
        ) {
            Box(
                modifier =
                    Modifier
                        .size(32.dp)
                        .background(Color.Black.copy(alpha = 0.58f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                AppIconGraphic(
                    AppIcon.OverflowVertical,
                    contentDescription,
                    Modifier.size(20.dp)
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(label(item)) },
                    onClick = {
                        onExpandedChange(false)
                        onSelected(item)
                    }
                )
            }
        }
    }
}
