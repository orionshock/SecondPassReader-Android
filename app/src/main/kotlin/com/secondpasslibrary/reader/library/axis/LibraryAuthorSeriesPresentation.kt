package com.secondpasslibrary.reader.library.axis

import com.secondpasslibrary.client.AuthorOrdering
import com.secondpasslibrary.client.LibraryAuthor
import com.secondpasslibrary.client.LibraryPreviewBook
import com.secondpasslibrary.client.LibrarySeries
import com.secondpasslibrary.client.PublicBookCoverReference
import com.secondpasslibrary.client.SeriesOrdering
import com.secondpasslibrary.reader.library.LibraryFailure

internal sealed interface LibraryAuthorSeriesPreviewBooksPresentation {
    data object Omitted : LibraryAuthorSeriesPreviewBooksPresentation

    data class Returned(val books: List<LibraryAuthorSeriesPreviewBookPresentation>) :
        LibraryAuthorSeriesPreviewBooksPresentation
}

internal data class LibraryAuthorSeriesPreviewBookPresentation(
    val id: String,
    val title: String,
    val cover: PublicBookCoverReference?
)

internal data class LibraryAuthorSeriesCardPresentation(
    val id: String,
    val name: String,
    val bookCountLabel: String,
    val previews: LibraryAuthorSeriesPreviewBooksPresentation
)

internal sealed interface LibraryAuthorSeriesDetailPresentation {
    val id: String

    data class Loading(override val id: String) : LibraryAuthorSeriesDetailPresentation

    data class Failure(override val id: String, val failure: LibraryFailure) :
        LibraryAuthorSeriesDetailPresentation

    data class Content(
        override val id: String,
        val name: String,
        val bookCountLabel: String,
        val description: String?
    ) : LibraryAuthorSeriesDetailPresentation
}

internal data class LibraryAuthorOrderingOption(val ordering: AuthorOrdering, val label: String)

internal data class LibrarySeriesOrderingOption(val ordering: SeriesOrdering, val label: String)

internal fun LibraryAuthor.toLibraryAuthorSeriesPresentation() =
    LibraryAuthorSeriesCardPresentation(
        id,
        name,
        bookCountLabel(bookCount),
        previewBooks.toPresentation()
    )

internal fun LibrarySeries.toLibraryAuthorSeriesPresentation() =
    LibraryAuthorSeriesCardPresentation(
        id,
        name,
        bookCountLabel(bookCount),
        previewBooks.toPresentation()
    )

internal fun PagedLibraryAxisDetailState<LibraryAuthor>.toAuthorDetailPresentation():
    LibraryAuthorSeriesDetailPresentation =
    when {
        loading -> LibraryAuthorSeriesDetailPresentation.Loading(id)

        failure != null -> LibraryAuthorSeriesDetailPresentation.Failure(id, failure)

        detail != null ->
            LibraryAuthorSeriesDetailPresentation.Content(
                id,
                detail.name,
                bookCountLabel(detail.bookCount),
                detail.biography.takeIf(String::isNotBlank)
            )

        else -> LibraryAuthorSeriesDetailPresentation.Loading(id)
    }

internal fun PagedLibraryAxisDetailState<LibrarySeries>.toSeriesDetailPresentation():
    LibraryAuthorSeriesDetailPresentation =
    when {
        loading -> LibraryAuthorSeriesDetailPresentation.Loading(id)

        failure != null -> LibraryAuthorSeriesDetailPresentation.Failure(id, failure)

        detail != null ->
            LibraryAuthorSeriesDetailPresentation.Content(
                id,
                detail.name,
                bookCountLabel(detail.bookCount),
                detail.summary.takeIf(String::isNotBlank)
            )

        else -> LibraryAuthorSeriesDetailPresentation.Loading(id)
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

private fun List<LibraryPreviewBook>?.toPresentation():
    LibraryAuthorSeriesPreviewBooksPresentation =
    this?.let { previews ->
        LibraryAuthorSeriesPreviewBooksPresentation.Returned(
            previews.take(LIBRARY_AXIS_PREVIEW_LIMIT).map { it.toPresentation() }
        )
    } ?: LibraryAuthorSeriesPreviewBooksPresentation.Omitted

private fun LibraryPreviewBook.toPresentation() = LibraryAuthorSeriesPreviewBookPresentation(
    id,
    title,
    cover
)

private fun bookCountLabel(count: Int) = "$count ${if (count == 1) "book" else "books"}"
