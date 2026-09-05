package com.secondpasslibrary.reader.bookdetail

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.secondpasslibrary.reader.design.richtext.ServerRichText

private const val COLLAPSED_DESCRIPTION_LINES = 8

@Composable
internal fun BookDescription(description: String?) {
    if (description.isNullOrBlank()) return
    Text("Description", style = MaterialTheme.typography.titleMedium)
    ServerRichText(
        value = description,
        style = MaterialTheme.typography.bodyMedium,
        collapsedMaxLines = COLLAPSED_DESCRIPTION_LINES,
        expandOverflow = true,
        moreLabel = "Show more",
        lessLabel = "Show less"
    )
}
