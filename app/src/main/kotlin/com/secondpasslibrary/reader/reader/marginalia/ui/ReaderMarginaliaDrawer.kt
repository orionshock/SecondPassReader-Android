package com.secondpasslibrary.reader.reader.marginalia.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsState
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationState
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayersState
import com.secondpasslibrary.reader.reader.ui.ReaderChromeColors

internal data class ReaderMarginaliaDrawerModel(
    val currentSessionId: String,
    val currentAnnotations: ReaderAnnotationsState,
    val layers: ReaderMarginaliaLayersState,
    val state: ReaderMarginaliaDrawerState,
    val colors: ReaderChromeColors,
    val currentEditable: Boolean,
    val mutationState: ReaderAnnotationMutationState
)

internal data class ReaderMarginaliaDrawerActions(
    val dismiss: () -> Unit,
    val retryCurrent: () -> Unit,
    val loadLayer: (String) -> Unit,
    val setLayerVisible: (String, Boolean) -> Unit,
    val loadMoreLayers: () -> Unit,
    val retryLayers: () -> Unit,
    val selectAnnotation: (ReaderAnnotation) -> Unit,
    val createBookmark: () -> Unit,
    val editHighlight: (ReaderAnnotation.Highlight) -> Unit,
    val deleteAnnotation: (ReaderAnnotation) -> Unit
)

@Composable
internal fun ReaderMarginaliaDrawer(
    currentSessionId: String,
    currentAnnotations: ReaderAnnotationsState,
    layers: ReaderMarginaliaLayersState,
    drawerState: ReaderMarginaliaDrawerState,
    colors: ReaderChromeColors,
    currentEditable: Boolean,
    mutationState: ReaderAnnotationMutationState,
    onDismiss: () -> Unit,
    onRetryCurrent: () -> Unit,
    onLoadLayer: (String) -> Unit,
    onSetLayerVisible: (String, Boolean) -> Unit,
    onLoadMoreLayers: () -> Unit,
    onRetryLayers: () -> Unit,
    onAnnotationSelected: (ReaderAnnotation) -> Unit,
    onCreateBookmark: () -> Unit,
    onEditHighlight: (ReaderAnnotation.Highlight) -> Unit,
    onDeleteAnnotation: (ReaderAnnotation) -> Unit
) {
    val model = ReaderMarginaliaDrawerModel(
        currentSessionId,
        currentAnnotations,
        layers,
        drawerState,
        colors,
        currentEditable,
        mutationState
    )
    val actions = ReaderMarginaliaDrawerActions(
        onDismiss,
        onRetryCurrent,
        onLoadLayer,
        onSetLayerVisible,
        onLoadMoreLayers,
        onRetryLayers,
        onAnnotationSelected,
        onCreateBookmark,
        onEditHighlight,
        onDeleteAnnotation
    )
    ReaderMarginaliaDrawerLayout(model, actions)
}

@Composable
private fun ReaderMarginaliaDrawerLayout(
    model: ReaderMarginaliaDrawerModel,
    actions: ReaderMarginaliaDrawerActions
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val drawerWidth = if (maxWidth >= WIDE_SCREEN_MIN_WIDTH) {
            (maxWidth * TABLET_DRAWER_FRACTION).coerceAtMost(TABLET_DRAWER_MAX_WIDTH)
        } else {
            (maxWidth * NARROW_DRAWER_FRACTION).coerceAtMost(NARROW_DRAWER_MAX_WIDTH)
        }
        val wide = drawerWidth >= WIDE_DRAWER_MIN_WIDTH
        BackHandler(enabled = !wide && model.state.showingLayerContent) {
            model.state.showLayerList()
        }
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = SCRIM_ALPHA))
                .clickable(onClick = actions.dismiss)
                .semantics { contentDescription = "Close annotations" }
        )
        Surface(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(drawerWidth),
            color = model.colors.panelBackground,
            contentColor = model.colors.content
        ) {
            ReaderMarginaliaDrawerContent(model, actions, wide)
        }
    }
}

@Composable
private fun ReaderMarginaliaDrawerContent(
    model: ReaderMarginaliaDrawerModel,
    actions: ReaderMarginaliaDrawerActions,
    wide: Boolean
) {
    Column {
        Text(
            "Marginalia",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleMedium
        )
        HorizontalDivider(
            color = model.colors.secondaryContent.copy(alpha = MARGINALIA_DIVIDER_ALPHA)
        )
        if (wide) {
            ReaderMarginaliaWideContent(model, actions)
        } else if (model.state.showingLayerContent) {
            ReaderSelectedLayerPane(
                model,
                actions,
                Modifier.fillMaxSize(),
                onNavigateBack = model.state::showLayerList
            )
        } else {
            ReaderMarginaliaLayerList(model, actions, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun ReaderMarginaliaWideContent(
    model: ReaderMarginaliaDrawerModel,
    actions: ReaderMarginaliaDrawerActions
) {
    Row(Modifier.fillMaxSize()) {
        ReaderMarginaliaLayerList(
            model,
            actions,
            Modifier.width(LAYER_PANE_WIDTH).fillMaxHeight()
        )
        VerticalDivider(
            modifier = Modifier.fillMaxHeight().width(1.dp),
            color = model.colors.secondaryContent.copy(alpha = MARGINALIA_DIVIDER_ALPHA)
        )
        ReaderSelectedLayerPane(
            model,
            actions,
            Modifier.weight(1f).fillMaxHeight()
        )
    }
}

private val WIDE_SCREEN_MIN_WIDTH = 760.dp
private val WIDE_DRAWER_MIN_WIDTH = 600.dp
private val TABLET_DRAWER_MAX_WIDTH = 760.dp
private val NARROW_DRAWER_MAX_WIDTH = 440.dp
private val LAYER_PANE_WIDTH = 260.dp
private const val TABLET_DRAWER_FRACTION = 0.72f
private const val NARROW_DRAWER_FRACTION = 0.94f
private const val SCRIM_ALPHA = 0.36f
internal const val MARGINALIA_DIVIDER_ALPHA = 0.35f
