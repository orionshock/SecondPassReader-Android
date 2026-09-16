package com.secondpasslibrary.reader.marginalia.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.design.book.PublicBookCover
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic
import com.secondpasslibrary.reader.marginalia.MarginaliaFailure
import com.secondpasslibrary.reader.marginalia.detail.annotations.AnnotationFailure
import com.secondpasslibrary.reader.marginalia.detail.annotations.AnnotationLoading
import com.secondpasslibrary.reader.marginalia.detail.annotations.ReadingSessionAnnotationCard
import com.secondpasslibrary.reader.marginalia.detail.annotations.ReadingSessionAnnotationsState
import com.secondpasslibrary.reader.marginalia.detail.annotations.toPresentation
import com.secondpasslibrary.reader.marginalia.detail.close.ReadingSessionCloseState
import com.secondpasslibrary.reader.marginalia.detail.metadata.ReadingSessionMetadataEditState
import com.secondpasslibrary.reader.marginalia.userMessage

@Composable
internal fun ReadingSessionDetailContent(
    detailState: ReadingSessionDetailState,
    annotationState: ReadingSessionAnnotationsState,
    metadataEditState: ReadingSessionMetadataEditState,
    closeState: ReadingSessionCloseState,
    actions: ReadingSessionDetailActions,
    modifier: Modifier = Modifier
) {
    when {
        detailState.loading -> DetailLoading(modifier)

        detailState.failure != null -> DetailFailure(
            detailState.failure,
            actions.retryDetail,
            modifier
        )

        detailState.detail != null -> LoadedSessionDetail(
            detailState.detail.toDetailPresentation(),
            annotationState,
            actions,
            modifier
        )
    }
    if (metadataEditState.open) ReadingSessionMetadataEditDialog(metadataEditState, actions)
    if (closeState.open) ReadingSessionCloseDialog(closeState, actions)
}

internal data class ReadingSessionDetailActions(
    val retryDetail: () -> Unit,
    val retryAnnotations: () -> Unit,
    val beginEdit: () -> Unit,
    val editNameChanged: (String) -> Unit,
    val editNotesChanged: (String) -> Unit,
    val saveEdit: () -> Unit,
    val cancelEdit: () -> Unit,
    val beginClose: () -> Unit,
    val closeNameChanged: (String) -> Unit,
    val closeNotesChanged: (String) -> Unit,
    val confirmClose: () -> Unit,
    val cancelClose: () -> Unit,
    val openBookMarginalia: () -> Unit,
    val openBookDetail: () -> Unit
)

@Composable
private fun LoadedSessionDetail(
    detail: ReadingSessionDetailPresentation,
    annotations: ReadingSessionAnnotationsState,
    actions: ReadingSessionDetailActions,
    modifier: Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SessionDetailHero(detail, actions) }
        item {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                "Marginalia",
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        when {
            annotations.loading -> item { AnnotationLoading() }

            annotations.failure != null -> item {
                AnnotationFailure(annotations.failure, actions.retryAnnotations)
            }

            annotations.loaded && annotations.annotations.isEmpty() -> item {
                Text(
                    "No Marginalia in this Reading Session.",
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
private fun SessionDetailHero(
    detail: ReadingSessionDetailPresentation,
    actions: ReadingSessionDetailActions
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(16.dp)) {
            if (maxWidth >= 820.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    SessionCover(detail, Modifier.width(170.dp).height(250.dp))
                    SessionMetadata(
                        detail,
                        actions.beginEdit,
                        actions.beginClose,
                        Modifier.weight(1f)
                    )
                    SessionBookActions(
                        detail,
                        actions,
                        vertical = true,
                        modifier = Modifier.width(170.dp)
                    )
                }
            } else if (maxWidth >= 560.dp) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        SessionCover(detail, Modifier.width(130.dp).height(192.dp))
                        SessionMetadata(
                            detail,
                            actions.beginEdit,
                            actions.beginClose,
                            Modifier.weight(1f)
                        )
                    }
                    SessionBookActions(detail, actions, vertical = false)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SessionCover(detail, Modifier.width(110.dp).height(162.dp))
                    SessionMetadata(
                        detail,
                        actions.beginEdit,
                        actions.beginClose,
                        Modifier.fillMaxWidth()
                    )
                    SessionBookActions(detail, actions, vertical = false)
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
private fun SessionMetadata(
    detail: ReadingSessionDetailPresentation,
    onBeginEdit: () -> Unit,
    onBeginClose: () -> Unit,
    modifier: Modifier
) {
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
        if (detail.active) {
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onBeginEdit) { Text("Edit Reading Session") }
                Button(
                    onClick = onBeginClose,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Close Reading Session")
                }
            }
        }
        detail.progressLocation?.let { MetadataLine("Progress", it) }
        MetadataLine("Started", detail.startedLabel)
        MetadataLine("Updated", detail.updatedLabel)
        detail.closedLabel?.let { MetadataLine("Closed", it) }
        detail.notes?.let { MetadataLine("Notes", it) }
    }
}

@Composable
private fun SessionBookActions(
    detail: ReadingSessionDetailPresentation,
    actions: ReadingSessionDetailActions,
    vertical: Boolean,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        SessionBookAction("Read Book", AppIcon.Book, false) {},
        SessionBookAction(
            "Book Marginalia",
            AppIcon.ReadingHistory,
            true,
            actions.openBookMarginalia
        ),
        SessionBookAction(
            "Book details",
            AppIcon.Library,
            detail.canOpenBook,
            actions.openBookDetail
        )
    )
    if (vertical) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { SessionBookActionTile(it, Modifier.fillMaxWidth()) }
        }
    } else {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { SessionBookActionTile(it, Modifier.weight(1f)) }
        }
    }
}

private data class SessionBookAction(
    val label: String,
    val icon: AppIcon,
    val enabled: Boolean,
    val onClick: () -> Unit
)

@Composable
private fun SessionBookActionTile(action: SessionBookAction, modifier: Modifier) {
    OutlinedButton(
        onClick = action.onClick,
        enabled = action.enabled,
        modifier = modifier.height(82.dp).semantics {
            if (!action.enabled) contentDescription = "${action.label}, unavailable"
        },
        contentPadding = PaddingValues(6.dp)
    ) {
        Column(
            Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AppIconGraphic(action.icon, null, Modifier.size(22.dp))
            Text(
                action.label,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
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
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
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
