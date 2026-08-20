package com.secondpasslibrary.reader.marginalia

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic
import com.secondpasslibrary.reader.design.marginalia.annotationHighlightPalette

@Composable
internal fun ReadingSessionAnnotationCard(model: ReadingSessionAnnotationPresentation) {
    when (model) {
        is ReadingSessionAnnotationPresentation.Bookmark -> BookmarkCard(model)
        is ReadingSessionAnnotationPresentation.Highlight -> HighlightCard(model)
    }
}

@Composable
private fun BookmarkCard(model: ReadingSessionAnnotationPresentation.Bookmark) {
    AnnotationSurface {
        AnnotationHeading(AppIcon.Bookmark, model.label, model.updatedLabel)
        model.locationLabel?.let { AnnotationLocation(it) }
    }
}

@Composable
private fun HighlightCard(model: ReadingSessionAnnotationPresentation.Highlight) {
    val palette = annotationHighlightPalette(model.tone)
    AnnotationSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.width(3.dp).heightIn(min = 76.dp).background(palette.accent))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                AnnotationHeading(
                    if (model.note == null) AppIcon.Highlight else AppIcon.HighlightWithNote,
                    model.label,
                    model.updatedLabel
                )
                Text(
                    model.quote,
                    modifier = Modifier.fillMaxWidth().background(
                        palette.background
                    ).padding(10.dp),
                    fontStyle = FontStyle.Italic,
                    style = MaterialTheme.typography.bodyMedium
                )
                model.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                model.locationLabel?.let { AnnotationLocation(it) }
            }
        }
    }
}

@Composable
private fun AnnotationSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun AnnotationHeading(icon: AppIcon, label: String, updatedLabel: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppIconGraphic(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(
            label,
            modifier = Modifier.padding(start = 8.dp).weight(1f),
            fontWeight = FontWeight.SemiBold
        )
        Text(
            updatedLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun AnnotationLocation(label: String) {
    Text(
        label,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium
    )
}

@Composable
internal fun AnnotationLoading() {
    Box(Modifier.fillMaxWidth().padding(22.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
internal fun AnnotationFailure(failure: MarginaliaFailure, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Annotations could not be loaded. ${failure.userMessage()}",
            color = MaterialTheme.colorScheme.error
        )
        OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
            Text("Retry annotations")
        }
    }
}
