package com.secondpasslibrary.reader.marginalia

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.design.book.PublicBookCover

@Composable
internal fun ReadingSessionDetailContent(
    detailState: ReadingSessionDetailState,
    annotationState: ReadingSessionAnnotationsState,
    onRetryDetail: () -> Unit,
    onRetryAnnotations: () -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        detailState.loading -> DetailLoading(modifier)

        detailState.failure != null -> DetailFailure(detailState.failure, onRetryDetail, modifier)

        detailState.detail != null -> LoadedSessionDetail(
            detailState.detail.toDetailPresentation(),
            annotationState,
            onRetryAnnotations,
            modifier
        )
    }
}

@Composable
private fun LoadedSessionDetail(
    detail: ReadingSessionDetailPresentation,
    annotations: ReadingSessionAnnotationsState,
    onRetryAnnotations: () -> Unit,
    modifier: Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SessionDetailHero(detail) }
        item {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 2.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Text(
                "Annotations",
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        when {
            annotations.loading -> item { AnnotationLoading() }

            annotations.failure != null -> item {
                AnnotationFailure(annotations.failure, onRetryAnnotations)
            }

            annotations.loaded && annotations.annotations.isEmpty() -> item {
                Text(
                    "No annotations in this reading session.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> items(annotations.annotations) { annotation ->
                ReadingSessionAnnotationCard(annotation.toPresentation())
            }
        }
    }
}

@Composable
private fun SessionDetailHero(detail: ReadingSessionDetailPresentation) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(16.dp)) {
            if (maxWidth >= 900.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    SessionCover(detail, Modifier.width(170.dp).height(250.dp))
                    SessionMetadata(detail, Modifier.weight(1f))
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SessionCover(detail, Modifier.width(130.dp).height(192.dp))
                    SessionMetadata(detail, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun SessionCover(detail: ReadingSessionDetailPresentation, modifier: Modifier) {
    PublicBookCover(detail.cover, detail.bookTitle, modifier)
}

@Composable
private fun SessionMetadata(detail: ReadingSessionDetailPresentation, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            detail.bookTitle,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            detail.sessionNameOrFallback(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleMedium
        )
        SessionStatusLine(detail)
        detail.progressLocation?.let { MetadataLine("Progress", it) }
        MetadataLine("Started", detail.startedLabel)
        MetadataLine("Updated", detail.updatedLabel)
        detail.closedLabel?.let { MetadataLine("Closed", it) }
        detail.closedNotice?.let {
            Text(
                it,
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun SessionStatusLine(detail: ReadingSessionDetailPresentation) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            detail.statusLabel,
            color = if (detail.active) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = MaterialTheme.typography.labelLarge
        )
        Text("\u2022", color = MaterialTheme.colorScheme.outline)
        Text(
            detail.annotationCountLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun MetadataLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$label:", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}

@Composable
private fun DetailLoading(modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun DetailFailure(failure: MarginaliaFailure, onRetry: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(failure.userMessage(), color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = 10.dp)) {
            Text("Retry")
        }
    }
}
