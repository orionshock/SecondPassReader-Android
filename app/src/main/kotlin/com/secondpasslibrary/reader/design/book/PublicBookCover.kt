package com.secondpasslibrary.reader.design.book

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.secondpasslibrary.client.PublicBookCoverReference
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic

@Composable
internal fun PublicBookCover(
    reference: PublicBookCoverReference?,
    title: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    Box(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        if (reference == null) {
            MissingBookCover(title)
        } else {
            SubcomposeAsyncImage(
                model = reference.url,
                contentDescription = "Cover of $title",
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                loading = { CoverLoading() },
                error = { MissingBookCover(title) },
                success = { SubcomposeAsyncImageContent() }
            )
        }
    }
}

@Composable
private fun CoverLoading() {
    CircularProgressIndicator(
        modifier = Modifier.size(22.dp),
        color = MaterialTheme.colorScheme.primary,
        strokeWidth = 2.dp
    )
}

@Composable
private fun MissingBookCover(title: String) {
    AppIconGraphic(
        icon = AppIcon.Library,
        contentDescription = "No cover available for $title",
        modifier = Modifier.size(36.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
