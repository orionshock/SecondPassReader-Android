package com.secondpasslibrary.reader.library

import com.secondpasslibrary.reader.library.axis.LibraryAuthorsState
import com.secondpasslibrary.reader.library.axis.LibrarySeriesState
import com.secondpasslibrary.reader.library.books.LibraryBooksState

internal val LibraryState.books: LibraryBooksState
    get() = requireNotNull(result.booksStateOrNull()) { "Expected a Books result surface." }

internal val LibraryState.authors: LibraryAuthorsState
    get() = (result as LibraryResultState.AuthorIndex).state

internal val LibraryState.series: LibrarySeriesState
    get() = (result as LibraryResultState.SeriesIndex).state
