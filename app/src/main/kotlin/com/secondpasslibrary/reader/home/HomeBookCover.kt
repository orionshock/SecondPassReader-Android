package com.secondpasslibrary.reader.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.secondpasslibrary.reader.design.book.PublicBookCover
import java.io.File

@Composable
internal fun HomeBookCover(
    cover: BookCoverPresentation,
    title: String,
    modifier: Modifier = Modifier,
    localCover: File? = null
) {
    PublicBookCover(
        reference = (cover as? BookCoverPresentation.Public)?.reference,
        title = title,
        modifier = modifier,
        localCover = localCover
    )
}
