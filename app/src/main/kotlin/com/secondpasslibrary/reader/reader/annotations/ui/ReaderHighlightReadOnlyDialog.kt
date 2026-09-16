package com.secondpasslibrary.reader.reader.annotations.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.reader.annotations.decoration.ReaderReadOnlyHighlightDetail
import com.secondpasslibrary.reader.reader.appearance.ReaderPalette

@Composable
internal fun ReaderHighlightReadOnlyDialog(
    detail: ReaderReadOnlyHighlightDetail,
    palette: ReaderPalette,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(detail.displayTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        Modifier.size(12.dp).background(
                            Color(detail.annotation.color.displayArgb),
                            CircleShape
                        )
                    )
                    Text(
                        if (detail.historical) "Historical · Read only" else "Closed · Read only",
                        color = palette.secondaryForeground
                    )
                }
                Text(
                    detail.annotation.quote,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    fontStyle = FontStyle.Italic
                )
                detail.annotation.note?.takeIf(String::isNotBlank)?.let { Text(it) }
                detail.annotation.locationLabel?.takeIf(String::isNotBlank)?.let {
                    Text(it, color = palette.secondaryForeground)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = palette.primaryForeground)
            ) { Text("Close") }
        },
        containerColor = palette.panelSurface,
        textContentColor = palette.primaryForeground,
        titleContentColor = palette.primaryForeground
    )
}

private val ReaderReadOnlyHighlightDetail.displayTitle: String
    get() = sessionName?.trim()?.takeIf(String::isNotEmpty)
        ?: if (historical) {
            startedAt?.take(10)?.let { "Previous read · $it" } ?: "Previous read"
        } else {
            "Current Reading Session"
        }
