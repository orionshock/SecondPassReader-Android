package com.secondpasslibrary.reader.design.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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

internal enum class AppBarContextEmphasis {
    MUTED,
    TITLE
}

internal data class AppBarPresentation(
    val navigation: AppBarNavigation,
    val title: String,
    val context: String? = null,
    val contextIcon: AppIcon? = null,
    val contextDetail: String? = null,
    val contextEmphasis: AppBarContextEmphasis = AppBarContextEmphasis.MUTED,
    val separator: String = " › ",
    val metadata: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContextualAppBar(
    presentation: AppBarPresentation,
    titleActions: @Composable RowScope.() -> Unit = {},
    onNavigation: () -> Unit
) {
    TopAppBar(
        title = { ContextualAppBarTitle(presentation, titleActions) },
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
private fun ContextualAppBarTitle(
    presentation: AppBarPresentation,
    titleActions: @Composable RowScope.() -> Unit
) {
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
            titleActions()
            ContextualAppBarMetadata(presentation.metadata)
        }
    }
}

@Composable
private fun ContextualAppBarContext(presentation: AppBarPresentation, availableWidth: Dp) {
    val context = presentation.context ?: return
    val contextColor =
        when (presentation.contextEmphasis) {
            AppBarContextEmphasis.MUTED -> MaterialTheme.colorScheme.onSurfaceVariant
            AppBarContextEmphasis.TITLE -> MaterialTheme.colorScheme.onSurface
        }
    val contextStyle =
        when (presentation.contextEmphasis) {
            AppBarContextEmphasis.MUTED -> MaterialTheme.typography.bodyMedium
            AppBarContextEmphasis.TITLE -> MaterialTheme.typography.titleLarge
        }
    Row(
        modifier =
            Modifier.widthIn(
                max = minOf(availableWidth * CONTEXT_WIDTH_FRACTION, 320.dp)
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = context,
            color = contextColor,
            style = contextStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        presentation.contextIcon?.let { icon ->
            AppIconGraphic(
                icon,
                null,
                Modifier.padding(horizontal = 4.dp).size(24.dp),
                contextColor
            )
        }
        presentation.contextDetail?.let { detail ->
            Text(
                text = detail,
                color = contextColor,
                style = contextStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    Text(
        text = presentation.separator,
        color = contextColor,
        style = contextStyle,
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

private const val CONTEXT_WIDTH_FRACTION = 0.55f
