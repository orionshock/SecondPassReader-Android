package com.secondpasslibrary.reader.reader.annotations.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic
import com.secondpasslibrary.reader.design.marginalia.annotationCountLabel
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsState
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationState
import com.secondpasslibrary.reader.reader.appearance.ReaderPalette

@Composable
internal fun ReaderAnnotationCollectionHeader(
    title: String,
    annotationCount: Int,
    palette: ReaderPalette,
    editable: Boolean = false,
    mutationState: ReaderAnnotationMutationState = ReaderAnnotationMutationState(),
    onCreateBookmark: () -> Unit = {}
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                annotationCountLabel(annotationCount),
                color = palette.secondaryForeground,
                style = MaterialTheme.typography.labelMedium
            )
            if (mutationState.pendingBookmark != null && mutationState.failure != null) {
                Text(
                    "Bookmark could not be saved. Tap retry.",
                    color = palette.secondaryForeground,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        if (editable) {
            IconButton(enabled = !mutationState.submitting, onClick = onCreateBookmark) {
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

/** Shared annotation collection presentation. Writability is supplied by the owning layer. */
@Composable
internal fun ReaderAnnotationCollectionPane(
    state: ReaderAnnotationsState,
    palette: ReaderPalette,
    onRetry: () -> Unit,
    onAnnotationSelected: (ReaderAnnotation) -> Unit,
    editable: Boolean = false,
    onEditHighlight: (ReaderAnnotation.Highlight) -> Unit = {},
    onDeleteAnnotation: (ReaderAnnotation) -> Unit = {}
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
            color = palette.secondaryForeground
        )

        else -> LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.loading) item { ReaderRefreshingAnnotations(palette) }
            if (state.failure != null) item { ReaderAnnotationFailure(onRetry) }
            items(state.annotations, key = ReaderAnnotation::id) { annotation ->
                ReaderAnnotationRow(
                    annotation,
                    palette,
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
private fun ReaderRefreshingAnnotations(palette: ReaderPalette) {
    Text(
        "Refreshing annotations\u2026",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        color = palette.secondaryForeground
    )
}
