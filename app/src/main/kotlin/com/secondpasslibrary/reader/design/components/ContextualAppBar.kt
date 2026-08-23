package com.secondpasslibrary.reader.design.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.Dp
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
    val context: String? = null,
    val contextIcon: AppIcon? = null,
    val contextDetail: String? = null,
    val separator: String = " › ",
    val metadata: String? = null
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
            ContextualAppBarContext(presentation, availableWidth)
            Text(
                text = presentation.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            ContextualAppBarMetadata(presentation.metadata)
        }
    }
}

@Composable
private fun ContextualAppBarContext(presentation: AppBarPresentation, availableWidth: Dp) {
    val context = presentation.context ?: return
    Row(
        modifier =
            Modifier.widthIn(
                max = minOf(availableWidth * CONTEXT_WIDTH_FRACTION, 220.dp)
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = context,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        presentation.contextIcon?.let { icon ->
            AppIconGraphic(
                icon,
                null,
                Modifier.size(22.dp).padding(start = 4.dp, end = 4.dp),
                MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        presentation.contextDetail?.let { detail ->
            Text(
                text = detail,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    Text(
        text = presentation.separator,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1
    )
}

@Composable
private fun ContextualAppBarMetadata(metadata: String?) {
    metadata ?: return
    Text(
        text = metadata,
        modifier = Modifier.padding(start = 10.dp, end = 8.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

private const val CONTEXT_WIDTH_FRACTION = 0.35f
