package com.secondpasslibrary.reader.library

import androidx.compose.runtime.Composable
import com.secondpasslibrary.reader.design.book.CompactBookGridCard
import com.secondpasslibrary.reader.design.book.CompactBookPresentation
import com.secondpasslibrary.reader.design.book.CompactBookRow

@Composable
internal fun LibraryBookRow(book: LibraryBookPresentation, onClick: () -> Unit) {
    CompactBookRow(book.toSharedPresentation(), onClick)
}

@Composable
internal fun LibraryBookGridCard(book: LibraryBookPresentation, onClick: () -> Unit) {
    CompactBookGridCard(book.toSharedPresentation(), onClick)
}

private fun LibraryBookPresentation.toSharedPresentation() = CompactBookPresentation(
    id,
    title,
    subtitle,
    authors,
    series,
    publisher,
    (cover as? LibraryBookCoverPresentation.Public)?.reference
)
