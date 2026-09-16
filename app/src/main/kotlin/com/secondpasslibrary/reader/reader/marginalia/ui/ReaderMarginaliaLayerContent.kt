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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic
import com.secondpasslibrary.reader.design.marginalia.annotationCountLabel
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsState
import com.secondpasslibrary.reader.reader.annotations.ui.ReaderAnnotationCollectionPane
import com.secondpasslibrary.reader.reader.appearance.ReaderPalette
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayerLoadState
import com.secondpasslibrary.reader.reader.marginalia.ReaderPreviousMarginaliaLayer
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus

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
            ReaderSelectedSessionHeader(previous, model, actions)
        }
        HorizontalDivider(
            color = model.palette.border
        )
        if (previous == null) {
            ReaderAnnotationCollectionPane(
                state = model.currentAnnotations,
                palette = model.palette,
                onRetry = actions.retryCurrent,
                onNavigateAnnotation = actions.navigateAnnotation,
                writable = model.currentEditable,
                onEditHighlight = actions.editHighlight,
                onDeleteAnnotation = actions.deleteAnnotation
            )
        } else {
            ReaderPreviousLayerContent(
                previous,
                model.palette,
                actions.loadLayer,
                actions.navigateAnnotation
            )
        }
    }
}

@Composable
private fun ReaderSelectedSessionHeader(
    previous: ReaderPreviousMarginaliaLayer?,
    model: ReaderMarginaliaDrawerModel,
    actions: ReaderMarginaliaDrawerActions
) {
    val current = previous == null && model.state.selectedLayerSessionId == model.currentSessionId
    val title = if (current) {
        model.sessionMetadata.metadata?.name?.takeIf(String::isNotBlank)
            ?: "Current Reading Session"
    } else {
        checkNotNull(previous).displayName
    }
    val count = previous?.summary?.annotationCount ?: model.currentAnnotations.annotations.size
    val status = when {
        previous != null -> "Historical"
        model.layers.currentLayer?.sessionStatus == ReaderSessionStatus.ACTIVE -> "Active"
        else -> "Closed"
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                "$status · ${annotationCountLabel(count)}",
                color = model.palette.secondaryForeground,
                style = MaterialTheme.typography.labelMedium
            )
        }
        if (current && model.currentMetadataEditable) {
            IconButton(onClick = actions.editCurrentSessionMetadata) {
                AppIconGraphic(AppIcon.Edit, "Edit current Reading Session")
            }
        }
        if (current && model.currentEditable) {
            IconButton(
                enabled = !model.mutationState.submitting,
                onClick = actions.createBookmark
            ) {
                AppIconGraphic(
                    AppIcon.AddBookmark,
                    if (model.mutationState.pendingBookmark == null) {
                        "Bookmark current location"
                    } else {
                        "Retry bookmark"
                    }
                )
            }
        }
    }
}

@Composable
private fun ReaderPreviousLayerContent(
    layer: ReaderPreviousMarginaliaLayer,
    palette: ReaderPalette,
    onLoadLayer: (String) -> Unit,
    onNavigateAnnotation: (ReaderAnnotation) -> Unit
) {
    when (layer.loadState) {
        ReaderMarginaliaLayerLoadState.NOT_LOADED -> LayerMessage(
            "Load this previous Reading Session to browse its Marginalia.",
            "Load"
        ) { onLoadLayer(layer.summary.sessionId) }

        ReaderMarginaliaLayerLoadState.LOADING ->
            ReaderMarginaliaCompactProgress("Loading Marginalia")

        ReaderMarginaliaLayerLoadState.FAILED -> LayerMessage(
            "Couldn’t load this previous Reading Session.",
            "Retry"
        ) { onLoadLayer(layer.summary.sessionId) }

        ReaderMarginaliaLayerLoadState.LOADED -> ReaderAnnotationCollectionPane(
            state = ReaderAnnotationsState(
                sessionId = layer.summary.sessionId,
                annotations = layer.annotations,
                loaded = true
            ),
            palette = palette,
            onRetry = {},
            onNavigateAnnotation = onNavigateAnnotation,
            writable = false,
            onEditHighlight = {},
            onDeleteAnnotation = {}
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
