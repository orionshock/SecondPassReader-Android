package com.secondpasslibrary.reader.reader.marginalia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic
import com.secondpasslibrary.reader.design.marginalia.annotationCountLabel
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayerLoadState
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayerVisibility
import com.secondpasslibrary.reader.reader.marginalia.ReaderPreviousMarginaliaLayer
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import com.secondpasslibrary.reader.reader.ui.ReaderChromeColors

@Composable
internal fun ReaderMarginaliaLayerList(
    model: ReaderMarginaliaDrawerModel,
    actions: ReaderMarginaliaDrawerActions,
    modifier: Modifier
) {
    val availableIds = model.layers.previousLayers
        .mapTo(mutableSetOf(), { it.summary.sessionId }) + model.currentSessionId
    LazyColumn(modifier) {
        item(key = model.currentSessionId) {
            ReaderMarginaliaLayerRow(
                title = "Current Session",
                detail = listOfNotNull(
                    model.layers.currentLayer?.sessionStatus?.displayLabel,
                    annotationCountLabel(model.currentAnnotations.annotations.size)
                ).joinToString(" · "),
                selected = model.state.selectedLayerSessionId == model.currentSessionId,
                colors = model.colors,
                onSelect = { model.state.select(model.currentSessionId, availableIds) }
            )
        }
        items(model.layers.previousLayers, key = { it.summary.sessionId }) { layer ->
            ReaderMarginaliaLayerRow(
                title = layer.displayName,
                detail = "Historical · ${annotationCountLabel(layer.summary.annotationCount ?: 0)}",
                selected = model.state.selectedLayerSessionId == layer.summary.sessionId,
                colors = model.colors,
                onSelect = { model.state.select(layer.summary.sessionId, availableIds) },
                action = {
                    ReaderMarginaliaLayerAction(
                        layer,
                        actions.loadLayer,
                        actions.setLayerVisible
                    )
                }
            )
        }
        if (model.layers.isInitialLoading) {
            item { ReaderMarginaliaCompactProgress("Loading previous sessions") }
        }
        if (model.layers.failure != null) {
            item {
                OutlinedButton(onClick = actions.retryLayers, modifier = Modifier.padding(12.dp)) {
                    Text("Retry sessions")
                }
            }
        }
        if (model.layers.hasMore) {
            item {
                OutlinedButton(
                    enabled = !model.layers.isAppending,
                    onClick = actions.loadMoreLayers,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(if (model.layers.isAppending) "Loading…" else "Load more sessions")
                }
            }
        }
    }
}

@Composable
private fun ReaderMarginaliaLayerRow(
    title: String,
    detail: String,
    selected: Boolean,
    colors: ReaderChromeColors,
    onSelect: () -> Unit,
    action: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(if (selected) colors.selectedBackground else Color.Transparent)
            .semantics {
                contentDescription = "Marginalia layer $title"
                this.selected = selected
            }
            .clickable(role = Role.Tab, onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                detail,
                color = colors.secondaryContent,
                style = MaterialTheme.typography.labelSmall
            )
        }
        action?.invoke()
    }
}

@Composable
private fun ReaderMarginaliaLayerAction(
    layer: ReaderPreviousMarginaliaLayer,
    onLoad: (String) -> Unit,
    onSetVisible: (String, Boolean) -> Unit
) {
    val sessionId = layer.summary.sessionId
    when (layer.loadState) {
        ReaderMarginaliaLayerLoadState.NOT_LOADED -> OutlinedButton(
            onClick = { onLoad(sessionId) },
            modifier = Modifier.semantics { contentDescription = "Load ${layer.displayName}" }
        ) { Text("Load") }

        ReaderMarginaliaLayerLoadState.LOADING -> CircularProgressIndicator(
            modifier = Modifier.width(24.dp),
            strokeWidth = 2.dp
        )

        ReaderMarginaliaLayerLoadState.FAILED -> OutlinedButton(
            onClick = { onLoad(sessionId) },
            modifier = Modifier.semantics { contentDescription = "Retry ${layer.displayName}" }
        ) { Text("Retry") }

        ReaderMarginaliaLayerLoadState.LOADED -> {
            val visible = layer.visibility == ReaderMarginaliaLayerVisibility.VISIBLE
            IconButton(onClick = { onSetVisible(sessionId, !visible) }) {
                AppIconGraphic(
                    if (visible) AppIcon.PreviousLayerVisible else AppIcon.PreviousLayerHidden,
                    if (visible) "Hide ${layer.displayName}" else "Show ${layer.displayName}"
                )
            }
        }
    }
}

internal val ReaderPreviousMarginaliaLayer.displayName: String
    get() = summary.sessionName?.trim()?.takeIf(String::isNotEmpty)
        ?: summary.startedAt?.take(10)?.let { "Previous read · $it" }
        ?: "Previous read"

private val ReaderSessionStatus.displayLabel: String
    get() = when (this) {
        ReaderSessionStatus.ACTIVE -> "Active"
        ReaderSessionStatus.CLOSED -> "Closed"
    }
