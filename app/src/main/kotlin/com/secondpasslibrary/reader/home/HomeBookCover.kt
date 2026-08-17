package com.secondpasslibrary.reader.home

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
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic

@Composable
internal fun HomeBookCover(
    cover: BookCoverPresentation,
    title: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        when (cover) {
            BookCoverPresentation.Missing -> MissingCoverGraphic(title)

            is BookCoverPresentation.Public ->
                SubcomposeAsyncImage(
                    model = cover.reference.url,
                    contentDescription = "Cover of $title",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = { CoverLoading() },
                    error = { MissingCoverGraphic(title) },
                    success = { SubcomposeAsyncImageContent() }
                )
        }
    }
}

@Composable
private fun CoverLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.dp
        )
    }
}

@Composable
private fun MissingCoverGraphic(title: String) {
    AppIconGraphic(
        icon = AppIcon.Library,
        contentDescription = "No cover available for $title",
        modifier = Modifier.size(36.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
