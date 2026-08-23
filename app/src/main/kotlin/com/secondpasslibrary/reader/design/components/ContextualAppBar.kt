package com.secondpasslibrary.reader.design.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic

internal enum class AppBarNavigation {
    MENU,
    BACK
}

internal data class AppBarPresentation(
    val navigation: AppBarNavigation,
    val title: String,
    val context: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContextualAppBar(presentation: AppBarPresentation, onNavigation: () -> Unit) {
    TopAppBar(
        title = { ContextualAppBarTitle(presentation) },
        navigationIcon = {
            IconButton(onClick = onNavigation) {
                val back = presentation.navigation == AppBarNavigation.BACK
                AppIconGraphic(
                    if (back) AppIcon.Back else AppIcon.NavigationMenu,
                    if (back) "Back" else "Open navigation drawer"
                )
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            )
    )
}

@Composable
private fun ContextualAppBarTitle(presentation: AppBarPresentation) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val availableWidth = maxWidth
        Row(verticalAlignment = Alignment.CenterVertically) {
            presentation.context?.let { context ->
                Text(
                    text = context,
                    modifier =
                        Modifier.widthIn(
                            max = minOf(availableWidth * CONTEXT_WIDTH_FRACTION, 180.dp)
                        ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = " › ",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
            }
            Text(
                text = presentation.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private const val CONTEXT_WIDTH_FRACTION = 0.35f
