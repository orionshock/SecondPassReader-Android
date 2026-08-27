package com.secondpasslibrary.reader.reader.annotations.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
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
import com.secondpasslibrary.reader.design.marginalia.formatMarginaliaTimestamp
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.appearance.ReaderPalette
import java.time.ZoneId

@Composable
internal fun ReaderAnnotationRow(
    annotation: ReaderAnnotation,
    readerPalette: ReaderPalette,
    onAnnotationSelected: (ReaderAnnotation) -> Unit,
    editable: Boolean,
    onEditHighlight: (ReaderAnnotation.Highlight) -> Unit,
    onDeleteAnnotation: (ReaderAnnotation) -> Unit
) {
    val tone = (annotation as? ReaderAnnotation.Highlight)?.color?.toTone()
    val palette = tone?.let { annotationHighlightPalette(it) }
    val locale = LocalConfiguration.current.locales[0]
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics { contentDescription = "Open annotation" }
            .clickable(role = Role.Button) { onAnnotationSelected(annotation) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AppIconGraphic(annotation.icon(), null, tint = readerPalette.secondaryForeground)
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
                        Text(it, color = readerPalette.secondaryForeground)
                    }
                }
            }
            Text(
                formatMarginaliaTimestamp(
                    annotation.updatedAt,
                    ZoneId.systemDefault(),
                    locale
                ),
                color = readerPalette.secondaryForeground
            )
        }
        if (editable) {
            ReaderAnnotationActions(annotation, onEditHighlight, onDeleteAnnotation)
        }
    }
}

@Composable
private fun ReaderAnnotationActions(
    annotation: ReaderAnnotation,
    onEdit: (ReaderAnnotation.Highlight) -> Unit,
    onDelete: (ReaderAnnotation) -> Unit
) {
    var expanded by remember(annotation.id) { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            AppIconGraphic(
                AppIcon.OverflowVertical,
                when (annotation) {
                    is ReaderAnnotation.Bookmark -> "Bookmark actions"
                    is ReaderAnnotation.Highlight -> "Highlight actions"
                }
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (annotation is ReaderAnnotation.Highlight) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    leadingIcon = { AppIconGraphic(AppIcon.EditAnnotation, null) },
                    onClick = {
                        expanded = false
                        onEdit(annotation)
                    }
                )
            }
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
