package com.secondpasslibrary.reader.reader.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic

/** Reader-local navigation chrome layered over the publication viewport. */
@Composable
internal fun ReaderChrome(
    title: String,
    colors: ReaderChromeColors,
    onNavigationMenuRequested: () -> Unit,
    onAppearanceRequested: () -> Unit,
    onAnnotationsRequested: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = READER_CHROME_HEIGHT),
        color = colors.background.copy(alpha = CHROME_ALPHA),
        contentColor = colors.secondaryContent
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNavigationMenuRequested) {
                AppIconGraphic(
                    icon = AppIcon.NavigationMenu,
                    contentDescription = "Reader menu"
                )
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = colors.secondaryContent,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(
                modifier = Modifier.size(READER_CHROME_CONTROL_SIZE),
                onClick = onAppearanceRequested
            ) {
                AppIconGraphic(
                    icon = AppIcon.Settings,
                    contentDescription = "Reading appearance"
                )
            }
            IconButton(
                modifier = Modifier.size(READER_CHROME_CONTROL_SIZE),
                onClick = onAnnotationsRequested
            ) {
                AppIconGraphic(
                    icon = AppIcon.Marginalia,
                    contentDescription = "Reading annotations"
                )
            }
        }
    }
}

private val READER_CHROME_HEIGHT = 48.dp
private val READER_CHROME_CONTROL_SIZE = 48.dp
private const val CHROME_ALPHA = 0.82f
