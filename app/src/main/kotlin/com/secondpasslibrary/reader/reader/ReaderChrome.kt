package com.secondpasslibrary.reader.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic

/** Reader-local navigation chrome layered over the publication viewport. */
@Composable
internal fun ReaderChrome(title: String, onReturnToBook: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = READER_CHROME_HEIGHT),
        color = MaterialTheme.colorScheme.surface.copy(alpha = CHROME_ALPHA),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    AppIconGraphic(
                        icon = AppIcon.NavigationMenu,
                        contentDescription = "Reader menu"
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Return to Book") },
                        leadingIcon = {
                            AppIconGraphic(AppIcon.Book, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onReturnToBook()
                        }
                    )
                }
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Keeps the title centered and reserves the future right-side action position.
            Box(Modifier.size(READER_CHROME_CONTROL_SIZE))
        }
    }
}

private val READER_CHROME_HEIGHT = 48.dp
private val READER_CHROME_CONTROL_SIZE = 48.dp
private const val CHROME_ALPHA = 0.82f
