package com.secondpasslibrary.reader.reader.annotations.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsState
import com.secondpasslibrary.reader.reader.appearance.ReaderPalette

/** Shared annotation collection presentation. Writability is supplied by the owning layer. */
@Composable
internal fun ReaderAnnotationCollectionPane(
    state: ReaderAnnotationsState,
    palette: ReaderPalette,
    onRetry: () -> Unit,
    onNavigateAnnotation: (ReaderAnnotation) -> Unit,
    writable: Boolean,
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
            "No Marginalia in this Reading Session.",
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
                    onNavigateAnnotation,
                    writable,
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
        Text("Couldn’t load Marginalia. Retry.")
        OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
            Text("Retry")
        }
    }
}

@Composable
private fun ReaderRefreshingAnnotations(palette: ReaderPalette) {
    Text(
        "Refreshing Marginalia",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        color = palette.secondaryForeground
    )
}
