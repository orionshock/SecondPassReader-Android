package com.secondpasslibrary.reader.library.books

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.secondpasslibrary.reader.design.book.PublicBookCover

@Composable
internal fun LibraryBookCover(
    cover: LibraryBookCoverPresentation,
    title: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    PublicBookCover(
        reference = (cover as? LibraryBookCoverPresentation.Public)?.reference,
        title = title,
        modifier = modifier,
        contentScale = contentScale
    )
}
