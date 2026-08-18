package com.secondpasslibrary.reader.library

import com.secondpasslibrary.client.AuthorOrdering
import com.secondpasslibrary.client.LibraryAuthor
import com.secondpasslibrary.client.LibraryPreviewBook
import com.secondpasslibrary.client.LibrarySeries
import com.secondpasslibrary.client.SeriesOrdering

internal sealed interface LibraryPreviewBooksPresentation {
    data object Omitted : LibraryPreviewBooksPresentation

    data class Returned(val books: List<LibraryPreviewBookPresentation>) :
        LibraryPreviewBooksPresentation
}

internal data class LibraryPreviewBookPresentation(
    val id: String,
    val title: String,
    val cover: LibraryBookCoverPresentation
)

internal data class LibraryEntityCardPresentation(
    val id: String,
    val name: String,
    val bookCountLabel: String,
    val previews: LibraryPreviewBooksPresentation
)

internal sealed interface LibrarySelectedEntityPresentation {
    val id: String

    data class Loading(override val id: String) : LibrarySelectedEntityPresentation

    data class Failure(override val id: String, val failure: LibraryFailure) :
        LibrarySelectedEntityPresentation

    data class Content(
        override val id: String,
        val name: String,
        val bookCountLabel: String,
        val description: String?
    ) : LibrarySelectedEntityPresentation
}

internal data class LibraryAuthorOrderingOption(val ordering: AuthorOrdering, val label: String)

internal data class LibrarySeriesOrderingOption(val ordering: SeriesOrdering, val label: String)

internal fun LibraryAuthor.toLibraryEntityPresentation() = LibraryEntityCardPresentation(
    id,
    name,
    bookCountLabel(bookCount),
    previewBooks.toPresentation()
)

internal fun LibrarySeries.toLibraryEntityPresentation() = LibraryEntityCardPresentation(
    id,
    name,
    bookCountLabel(bookCount),
    previewBooks.toPresentation()
)

internal fun LibraryEntityDetailState<LibraryAuthor>.toAuthorDetailPresentation():
    LibrarySelectedEntityPresentation =
    when {
        loading -> LibrarySelectedEntityPresentation.Loading(id)

        failure != null -> LibrarySelectedEntityPresentation.Failure(id, failure)

        detail != null ->
            LibrarySelectedEntityPresentation.Content(
                id,
                detail.name,
                bookCountLabel(detail.bookCount),
                detail.biography.takeIf(String::isNotBlank)
            )

        else -> LibrarySelectedEntityPresentation.Loading(id)
    }

internal fun LibraryEntityDetailState<LibrarySeries>.toSeriesDetailPresentation():
    LibrarySelectedEntityPresentation =
    when {
        loading -> LibrarySelectedEntityPresentation.Loading(id)

        failure != null -> LibrarySelectedEntityPresentation.Failure(id, failure)

        detail != null ->
            LibrarySelectedEntityPresentation.Content(
                id,
                detail.name,
                bookCountLabel(detail.bookCount),
                detail.summary.takeIf(String::isNotBlank)
            )

        else -> LibrarySelectedEntityPresentation.Loading(id)
    }

internal fun authorOrderingOptions() = listOf(
    LibraryAuthorOrderingOption(AuthorOrdering.NAME, "Name A-Z"),
    LibraryAuthorOrderingOption(AuthorOrdering.NAME_DESCENDING, "Name Z-A"),
    LibraryAuthorOrderingOption(AuthorOrdering.BOOK_COUNT_DESCENDING, "Most books"),
    LibraryAuthorOrderingOption(AuthorOrdering.BOOK_COUNT, "Fewest books")
)

internal fun seriesOrderingOptions() = listOf(
    LibrarySeriesOrderingOption(SeriesOrdering.NAME, "Name A-Z"),
    LibrarySeriesOrderingOption(SeriesOrdering.NAME_DESCENDING, "Name Z-A"),
    LibrarySeriesOrderingOption(SeriesOrdering.BOOK_COUNT_DESCENDING, "Most books"),
    LibrarySeriesOrderingOption(SeriesOrdering.BOOK_COUNT, "Fewest books")
)

internal fun AuthorOrdering.libraryLabel() =
    authorOrderingOptions().first { it.ordering == this }.label

internal fun SeriesOrdering.libraryLabel() =
    seriesOrderingOptions().first { it.ordering == this }.label

private fun List<LibraryPreviewBook>?.toPresentation(): LibraryPreviewBooksPresentation =
    this?.let { previews ->
        LibraryPreviewBooksPresentation.Returned(
            previews.take(LIBRARY_AXIS_PREVIEW_LIMIT).map { it.toPresentation() }
        )
    } ?: LibraryPreviewBooksPresentation.Omitted

private fun LibraryPreviewBook.toPresentation() = LibraryPreviewBookPresentation(
    id,
    title,
    cover?.let(LibraryBookCoverPresentation::Public)
        ?: LibraryBookCoverPresentation.Missing
)

private fun bookCountLabel(count: Int) = "$count ${if (count == 1) "book" else "books"}"
