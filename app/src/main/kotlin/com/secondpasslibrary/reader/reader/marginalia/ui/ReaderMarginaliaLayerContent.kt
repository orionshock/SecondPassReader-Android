package com.secondpasslibrary.reader.reader.marginalia.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsState
import com.secondpasslibrary.reader.reader.annotations.ui.ReaderAnnotationCollectionHeader
import com.secondpasslibrary.reader.reader.annotations.ui.ReaderAnnotationCollectionPane
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayerLoadState
import com.secondpasslibrary.reader.reader.marginalia.ReaderPreviousMarginaliaLayer
import com.secondpasslibrary.reader.reader.ui.ReaderChromeColors

@Composable
internal fun ReaderSelectedLayerPane(
    model: ReaderMarginaliaDrawerModel,
    actions: ReaderMarginaliaDrawerActions,
    modifier: Modifier,
    onNavigateBack: (() -> Unit)? = null
) {
    val previous = model.layers.previousLayers.find {
        it.summary.sessionId == model.state.selectedLayerSessionId
    }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            onNavigateBack?.let {
                IconButton(onClick = it) { AppIconGraphic(AppIcon.Back, "Back to layers") }
            }
            ReaderAnnotationCollectionHeader(
                title = previous?.displayName ?: "Current Session",
                annotationCount = previous?.summary?.annotationCount
                    ?: model.currentAnnotations.annotations.size,
                colors = model.colors,
                editable = previous == null &&
                    model.state.selectedLayerSessionId == model.currentSessionId &&
                    model.currentEditable,
                mutationState = model.mutationState,
                onCreateBookmark = actions.createBookmark
            )
        }
        HorizontalDivider(
            color = model.colors.secondaryContent.copy(alpha = MARGINALIA_DIVIDER_ALPHA)
        )
        if (previous == null) {
            ReaderAnnotationCollectionPane(
                state = model.currentAnnotations,
                colors = model.colors,
                onRetry = actions.retryCurrent,
                onAnnotationSelected = actions.selectAnnotation,
                editable = model.currentEditable,
                onEditHighlight = actions.editHighlight,
                onDeleteAnnotation = actions.deleteAnnotation
            )
        } else {
            ReaderPreviousLayerContent(
                previous,
                model.colors,
                actions.loadLayer,
                actions.selectAnnotation
            )
        }
    }
}

@Composable
private fun ReaderPreviousLayerContent(
    layer: ReaderPreviousMarginaliaLayer,
    colors: ReaderChromeColors,
    onLoadLayer: (String) -> Unit,
    onAnnotationSelected: (ReaderAnnotation) -> Unit
) {
    when (layer.loadState) {
        ReaderMarginaliaLayerLoadState.NOT_LOADED -> LayerMessage(
            "Load this historical Session to browse its annotations.",
            "Load"
        ) { onLoadLayer(layer.summary.sessionId) }

        ReaderMarginaliaLayerLoadState.LOADING ->
            ReaderMarginaliaCompactProgress("Loading annotations")

        ReaderMarginaliaLayerLoadState.FAILED -> LayerMessage(
            "This historical Session could not be loaded.",
            "Retry"
        ) { onLoadLayer(layer.summary.sessionId) }

        ReaderMarginaliaLayerLoadState.LOADED -> ReaderAnnotationCollectionPane(
            state = ReaderAnnotationsState(
                sessionId = layer.summary.sessionId,
                annotations = layer.annotations,
                loaded = true
            ),
            colors = colors,
            onRetry = {},
            onAnnotationSelected = onAnnotationSelected,
            editable = false
        )
    }
}

@Composable
private fun LayerMessage(message: String, action: String, onAction: () -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(message)
        OutlinedButton(onClick = onAction) { Text(action) }
    }
}

@Composable
internal fun ReaderMarginaliaCompactProgress(description: String) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.width(24.dp), strokeWidth = 2.dp)
        Text(description)
    }
}
