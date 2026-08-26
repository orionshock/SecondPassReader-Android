package com.secondpasslibrary.reader.reader.annotations.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic
import com.secondpasslibrary.reader.design.marginalia.annotationCountLabel
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsState
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationState
import com.secondpasslibrary.reader.reader.ui.ReaderChromeColors

@Composable
internal fun ReaderAnnotationsDrawer(
    state: ReaderAnnotationsState,
    colors: ReaderChromeColors,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onAnnotationSelected: (ReaderAnnotation) -> Unit,
    editable: Boolean = false,
    mutationState: ReaderAnnotationMutationState = ReaderAnnotationMutationState(),
    onCreateBookmark: () -> Unit = {},
    onEditHighlight: (ReaderAnnotation.Highlight) -> Unit = {},
    onDeleteAnnotation: (ReaderAnnotation) -> Unit = {}
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = SCRIM_ALPHA))
                .clickable(onClick = onDismiss)
                .semantics { contentDescription = "Close annotations" }
        )
        Surface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .widthIn(min = DRAWER_MIN_WIDTH, max = DRAWER_MAX_WIDTH),
            color = colors.panelBackground,
            contentColor = colors.content
        ) {
            Column {
                ReaderAnnotationsHeader(
                    state,
                    colors,
                    editable,
                    mutationState,
                    onCreateBookmark
                )
                HorizontalDivider(color = colors.secondaryContent.copy(alpha = DIVIDER_ALPHA))
                ReaderAnnotationsContent(
                    state,
                    colors,
                    onRetry,
                    onAnnotationSelected,
                    editable,
                    onEditHighlight,
                    onDeleteAnnotation
                )
            }
        }
    }
}

@Composable
private fun ReaderAnnotationsHeader(
    state: ReaderAnnotationsState,
    colors: ReaderChromeColors,
    editable: Boolean,
    mutationState: ReaderAnnotationMutationState,
    onCreateBookmark: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Annotations", style = MaterialTheme.typography.titleMedium)
            if (state.loaded) {
                Text(
                    annotationCountLabel(state.annotations.size),
                    color = colors.secondaryContent,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            if (mutationState.pendingBookmark != null && mutationState.failure != null) {
                Text(
                    "Bookmark could not be saved. Tap retry.",
                    color = colors.secondaryContent,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        if (editable) {
            IconButton(
                enabled = !mutationState.submitting,
                onClick = onCreateBookmark
            ) {
                AppIconGraphic(
                    AppIcon.AddBookmark,
                    if (mutationState.pendingBookmark == null) {
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
private fun ReaderAnnotationsContent(
    state: ReaderAnnotationsState,
    colors: ReaderChromeColors,
    onRetry: () -> Unit,
    onAnnotationSelected: (ReaderAnnotation) -> Unit,
    editable: Boolean,
    onEditHighlight: (ReaderAnnotation.Highlight) -> Unit,
    onDeleteAnnotation: (ReaderAnnotation) -> Unit
) {
    when {
        state.loading && state.annotations.isEmpty() -> Box(
            Modifier.fillMaxWidth().padding(24.dp),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }

        state.failure != null && state.annotations.isEmpty() -> ReaderAnnotationFailure(onRetry)

        state.loaded && state.annotations.isEmpty() -> Text(
            "No annotations in this reading session.",
            modifier = Modifier.padding(16.dp),
            color = colors.secondaryContent
        )

        else -> LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.loading) item { ReaderRefreshingAnnotations(colors) }
            if (state.failure != null) item { ReaderAnnotationFailure(onRetry) }
            items(state.annotations, key = ReaderAnnotation::id) { annotation ->
                ReaderAnnotationRow(
                    annotation,
                    colors,
                    onAnnotationSelected,
                    editable,
                    onEditHighlight,
                    onDeleteAnnotation
                )
            }
        }
    }
}

@Composable
private fun ReaderAnnotationFailure(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Annotations could not be loaded.")
        OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
            Text("Retry")
        }
    }
}

@Composable
private fun ReaderRefreshingAnnotations(colors: ReaderChromeColors) {
    Text(
        "Refreshing annotations\u2026",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        color = colors.secondaryContent
    )
}

private val DRAWER_MIN_WIDTH = 320.dp
private val DRAWER_MAX_WIDTH = 420.dp
private const val SCRIM_ALPHA = 0.36f
private const val DIVIDER_ALPHA = 0.35f
