package com.secondpasslibrary.reader.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic
import com.secondpasslibrary.reader.design.marginalia.AnnotationHighlightTone
import com.secondpasslibrary.reader.design.marginalia.annotationHighlightPalette
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationsState
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
internal fun ReaderAnnotationsDrawer(
    state: ReaderAnnotationsState,
    colors: ReaderChromeColors,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onAnnotationSelected: (ReaderAnnotation) -> Unit,
    editable: Boolean = false,
    onEditHighlight: (ReaderAnnotation.Highlight) -> Unit = {},
    onDeleteHighlight: (ReaderAnnotation.Highlight) -> Unit = {}
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
                ReaderAnnotationsHeader(state, colors)
                HorizontalDivider(color = colors.secondaryContent.copy(alpha = DIVIDER_ALPHA))
                ReaderAnnotationsContent(
                    state,
                    colors,
                    onRetry,
                    onAnnotationSelected,
                    editable,
                    onEditHighlight,
                    onDeleteHighlight
                )
            }
        }
    }
}

@Composable
private fun ReaderAnnotationsHeader(state: ReaderAnnotationsState, colors: ReaderChromeColors) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text("Annotations", style = MaterialTheme.typography.titleMedium)
        if (state.loaded) {
            Text(
                annotationCountLabel(state.annotations.size),
                color = colors.secondaryContent,
                style = MaterialTheme.typography.labelMedium
            )
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
    onDeleteHighlight: (ReaderAnnotation.Highlight) -> Unit
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
                    onDeleteHighlight
                )
            }
        }
    }
}

@Composable
private fun ReaderAnnotationRow(
    annotation: ReaderAnnotation,
    colors: ReaderChromeColors,
    onAnnotationSelected: (ReaderAnnotation) -> Unit,
    editable: Boolean,
    onEditHighlight: (ReaderAnnotation.Highlight) -> Unit,
    onDeleteHighlight: (ReaderAnnotation.Highlight) -> Unit
) {
    val tone = (annotation as? ReaderAnnotation.Highlight)?.color?.toTone()
    val palette = tone?.let { annotationHighlightPalette(it) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics { contentDescription = "Open annotation" }
            .clickable(role = Role.Button) { onAnnotationSelected(annotation) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AppIconGraphic(annotation.icon(), null, tint = colors.secondaryContent)
        palette?.let {
            Box(Modifier.width(3.dp).heightIn(min = 62.dp).background(it.accent))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            when (annotation) {
                is ReaderAnnotation.Bookmark -> Text(
                    annotation.locationLabel ?: "Bookmark",
                    fontWeight = FontWeight.Medium
                )

                is ReaderAnnotation.Highlight -> {
                    Text(
                        annotation.quote,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(checkNotNull(palette).background)
                            .padding(8.dp),
                        fontStyle = FontStyle.Italic
                    )
                    annotation.note?.takeIf(String::isNotBlank)?.let { Text(it) }
                    annotation.locationLabel?.let {
                        Text(it, color = colors.secondaryContent)
                    }
                }
            }
            Text(formatAnnotationTimestamp(annotation.updatedAt), color = colors.secondaryContent)
        }
        if (editable && annotation is ReaderAnnotation.Highlight) {
            ReaderHighlightActions(annotation, onEditHighlight, onDeleteHighlight)
        }
    }
}

@Composable
private fun ReaderHighlightActions(
    annotation: ReaderAnnotation.Highlight,
    onEdit: (ReaderAnnotation.Highlight) -> Unit,
    onDelete: (ReaderAnnotation.Highlight) -> Unit
) {
    var expanded by remember(annotation.id) { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            AppIconGraphic(AppIcon.OverflowVertical, "Highlight actions")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Edit") },
                leadingIcon = { AppIconGraphic(AppIcon.EditAnnotation, null) },
                onClick = {
                    expanded = false
                    onEdit(annotation)
                }
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = { AppIconGraphic(AppIcon.Delete, null) },
                onClick = {
                    expanded = false
                    onDelete(annotation)
                }
            )
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

private fun ReaderAnnotation.icon(): AppIcon = when (this) {
    is ReaderAnnotation.Bookmark -> AppIcon.Bookmark

    is ReaderAnnotation.Highlight -> if (note.isNullOrBlank()) {
        AppIcon.Highlight
    } else {
        AppIcon.HighlightWithNote
    }
}

private fun ReaderAnnotationColor.toTone(): AnnotationHighlightTone = when (this) {
    ReaderAnnotationColor.YELLOW -> AnnotationHighlightTone.YELLOW
    ReaderAnnotationColor.GREEN -> AnnotationHighlightTone.GREEN
    ReaderAnnotationColor.BLUE -> AnnotationHighlightTone.BLUE
    ReaderAnnotationColor.PINK -> AnnotationHighlightTone.PINK
    ReaderAnnotationColor.PURPLE -> AnnotationHighlightTone.PURPLE
    ReaderAnnotationColor.ORANGE -> AnnotationHighlightTone.ORANGE
}

private fun annotationCountLabel(count: Int) =
    if (count == 1) "1 annotation" else "$count annotations"

private fun formatAnnotationTimestamp(value: String): String {
    val instant = runCatching { Instant.parse(value) }
        .recoverCatching { OffsetDateTime.parse(value).toInstant() }
        .getOrNull() ?: return value
    return DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(instant)
}

private val DRAWER_MIN_WIDTH = 320.dp
private val DRAWER_MAX_WIDTH = 420.dp
private const val SCRIM_ALPHA = 0.36f
private const val DIVIDER_ALPHA = 0.35f
