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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsState
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationState
import com.secondpasslibrary.reader.reader.appearance.ReaderPalette
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayersState
import com.secondpasslibrary.reader.reader.session.ReaderSessionMetadataState

internal data class ReaderMarginaliaDrawerModel(
    val currentSessionId: String,
    val currentAnnotations: ReaderAnnotationsState,
    val layers: ReaderMarginaliaLayersState,
    val autoShowPrevious: Boolean,
    val state: ReaderMarginaliaDrawerState,
    val palette: ReaderPalette,
    val currentEditable: Boolean,
    val currentMetadataEditable: Boolean,
    val mutationState: ReaderAnnotationMutationState,
    val sessionMetadata: ReaderSessionMetadataState
)

internal data class ReaderMarginaliaDrawerActions(
    val dismiss: () -> Unit,
    val retryCurrent: () -> Unit,
    val loadLayer: (String) -> Unit,
    val setLayerVisible: (String, Boolean) -> Unit,
    val showAllPrevious: () -> Unit,
    val hideAllPrevious: () -> Unit,
    val autoShowPreviousChanged: (Boolean) -> Unit,
    val loadMoreLayers: () -> Unit,
    val retryLayers: () -> Unit,
    val navigateAnnotation: (ReaderAnnotation) -> Unit,
    val createBookmark: () -> Unit,
    val editHighlight: (ReaderAnnotation.Highlight) -> Unit,
    val deleteAnnotation: (ReaderAnnotation) -> Unit,
    val editCurrentSessionMetadata: () -> Unit,
    val currentSessionNameChanged: (String) -> Unit,
    val currentSessionNotesChanged: (String) -> Unit,
    val saveCurrentSessionMetadata: () -> Unit,
    val dismissCurrentSessionMetadataEditor: () -> Unit
)

@Composable
internal fun ReaderMarginaliaDrawer(
    currentSessionId: String,
    currentAnnotations: ReaderAnnotationsState,
    layers: ReaderMarginaliaLayersState,
    autoShowPrevious: Boolean,
    drawerState: ReaderMarginaliaDrawerState,
    palette: ReaderPalette,
    currentEditable: Boolean,
    currentMetadataEditable: Boolean,
    mutationState: ReaderAnnotationMutationState,
    sessionMetadata: ReaderSessionMetadataState,
    onDismiss: () -> Unit,
    onRetryCurrent: () -> Unit,
    onLoadLayer: (String) -> Unit,
    onSetLayerVisible: (String, Boolean) -> Unit,
    onShowAllPrevious: () -> Unit,
    onHideAllPrevious: () -> Unit,
    onAutoShowPreviousChanged: (Boolean) -> Unit,
    onLoadMoreLayers: () -> Unit,
    onRetryLayers: () -> Unit,
    onNavigateAnnotation: (ReaderAnnotation) -> Unit,
    onCreateBookmark: () -> Unit,
    onEditHighlight: (ReaderAnnotation.Highlight) -> Unit,
    onDeleteAnnotation: (ReaderAnnotation) -> Unit,
    onEditCurrentSessionMetadata: () -> Unit,
    onCurrentSessionNameChanged: (String) -> Unit,
    onCurrentSessionNotesChanged: (String) -> Unit,
    onSaveCurrentSessionMetadata: () -> Unit,
    onDismissCurrentSessionMetadataEditor: () -> Unit
) {
    val model = ReaderMarginaliaDrawerModel(
        currentSessionId,
        currentAnnotations,
        layers,
        autoShowPrevious,
        drawerState,
        palette,
        currentEditable,
        currentMetadataEditable,
        mutationState,
        sessionMetadata
    )
    val actions = ReaderMarginaliaDrawerActions(
        onDismiss,
        onRetryCurrent,
        onLoadLayer,
        onSetLayerVisible,
        onShowAllPrevious,
        onHideAllPrevious,
        onAutoShowPreviousChanged,
        onLoadMoreLayers,
        onRetryLayers,
        onNavigateAnnotation,
        onCreateBookmark,
        onEditHighlight,
        onDeleteAnnotation,
        onEditCurrentSessionMetadata,
        onCurrentSessionNameChanged,
        onCurrentSessionNotesChanged,
        onSaveCurrentSessionMetadata,
        onDismissCurrentSessionMetadataEditor
    )
    ReaderMarginaliaDrawerLayout(model, actions)
    ReaderSessionMetadataDialog(
        state = model.sessionMetadata,
        palette = model.palette,
        onNameChanged = actions.currentSessionNameChanged,
        onNotesChanged = actions.currentSessionNotesChanged,
        onSave = actions.saveCurrentSessionMetadata,
        onDismiss = actions.dismissCurrentSessionMetadataEditor
    )
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
            Modifier.fillMaxSize().background(model.palette.scrim)
                .clickable(onClick = actions.dismiss)
                .semantics { contentDescription = "Close Marginalia" }
        )
        Surface(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(drawerWidth),
            color = model.palette.panelSurface,
            contentColor = model.palette.primaryForeground
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
            color = model.palette.border
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
