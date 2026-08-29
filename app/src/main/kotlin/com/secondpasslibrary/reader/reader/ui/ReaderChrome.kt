package com.secondpasslibrary.reader.reader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic
import com.secondpasslibrary.reader.reader.appearance.ReaderPalette

/** Transparent positioning layer containing only local floating Reader controls. */
@Composable
internal fun ReaderChrome(
    title: String,
    palette: ReaderPalette,
    visible: Boolean = true,
    onNavigationMenuRequested: () -> Unit,
    onAppearanceRequested: () -> Unit,
    onAnnotationsRequested: () -> Unit
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().statusBarsPadding().padding(
                start = READER_CHROME_EDGE_PADDING,
                top = READER_CHROME_TOP_PADDING,
                end = READER_CHROME_EDGE_PADDING
            ).testTag(READER_CHROME_POSITIONER_TAG)
        ) {
            val leftClusterMaxWidth =
                (maxWidth - READER_CHROME_RIGHT_CLUSTER_WIDTH - READER_CHROME_CLUSTER_GAP)
                    .coerceAtLeast(READER_CHROME_MIN_LEFT_WIDTH)
                    .coerceAtMost(READER_CHROME_MAX_LEFT_WIDTH)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                ReaderChromeSurface(
                    palette,
                    Modifier.widthIn(max = leftClusterMaxWidth)
                        .testTag(READER_CHROME_LEFT_CLUSTER_TAG)
                ) {
                    IconButton(
                        modifier = Modifier.size(READER_CHROME_CONTROL_SIZE),
                        onClick = onNavigationMenuRequested
                    ) {
                        AppIconGraphic(AppIcon.NavigationMenu, "Reader menu")
                    }
                    Text(
                        text = title,
                        modifier = Modifier.widthIn(
                            max = leftClusterMaxWidth - READER_CHROME_CONTROL_SIZE
                        ).padding(end = 12.dp),
                        color = palette.secondaryForeground,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                ReaderChromeSurface(
                    palette,
                    Modifier.testTag(READER_CHROME_RIGHT_CLUSTER_TAG)
                ) {
                    IconButton(
                        modifier = Modifier.size(READER_CHROME_CONTROL_SIZE),
                        onClick = onAppearanceRequested
                    ) {
                        AppIconGraphic(AppIcon.Settings, "Reading appearance")
                    }
                    IconButton(
                        modifier = Modifier.size(READER_CHROME_CONTROL_SIZE),
                        onClick = onAnnotationsRequested
                    ) {
                        AppIconGraphic(AppIcon.Marginalia, "Reading annotations")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderChromeSurface(
    palette: ReaderPalette,
    modifier: Modifier,
    content: @Composable () -> Unit
) = Row(
    modifier = modifier.height(READER_CHROME_TOUCH_HEIGHT).drawBehind {
        val inset = READER_CHROME_VISUAL_INSET.toPx()
        val surfaceSize = Size(size.width, size.height - inset * 2)
        val corner = CornerRadius(surfaceSize.height / 2, surfaceSize.height / 2)
        drawRoundRect(
            color = palette.floatingSurface.copy(alpha = CHROME_SURFACE_ALPHA),
            topLeft = Offset(0f, inset),
            size = surfaceSize,
            cornerRadius = corner
        )
        drawRoundRect(
            color = palette.border,
            topLeft = Offset(0f, inset),
            size = surfaceSize,
            cornerRadius = corner,
            style = Stroke(width = 1.dp.toPx())
        )
    },
    verticalAlignment = Alignment.CenterVertically
) {
    CompositionLocalProvider(LocalContentColor provides palette.primaryForeground) { content() }
}

internal const val READER_CHROME_POSITIONER_TAG = "reader_chrome_positioner"
internal const val READER_CHROME_LEFT_CLUSTER_TAG = "reader_chrome_left_cluster"
internal const val READER_CHROME_RIGHT_CLUSTER_TAG = "reader_chrome_right_cluster"

internal val READER_PUBLICATION_TOP_SAFE_INSET = 60.dp
private val READER_CHROME_TOP_PADDING = 8.dp
private val READER_CHROME_EDGE_PADDING = 12.dp
private val READER_CHROME_CLUSTER_GAP = 8.dp
private val READER_CHROME_CONTROL_SIZE = 48.dp
private val READER_CHROME_TOUCH_HEIGHT = 48.dp
private val READER_CHROME_VISUAL_INSET = 5.dp
private val READER_CHROME_RIGHT_CLUSTER_WIDTH = 96.dp
private val READER_CHROME_MIN_LEFT_WIDTH = 120.dp
private val READER_CHROME_MAX_LEFT_WIDTH = 360.dp
private const val CHROME_SURFACE_ALPHA = 0.92f
