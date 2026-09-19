package com.secondpasslibrary.reader.app.shell

import com.secondpasslibrary.reader.design.book.BookCardAction

internal fun AppNavigator.handleLibraryBookAction(action: BookCardAction) {
    when (action) {
        is BookCardAction.BookDetails ->
            openBookDetail(action.bookId, BookDetailReturnTarget.Library)

        is BookCardAction.ReadingSessions -> openLibraryBookMarginalia(action.bookId)

        is BookCardAction.Author -> openLibraryAuthor(action.authorId)

        is BookCardAction.Series -> openLibrarySeries(action.seriesId)

        is BookCardAction.MakeAvailableOffline,
        is BookCardAction.RemoveDownload ->
            error("Offline Book actions are handled by the Book surface.")
    }
}

internal fun AppNavigator.handleHomeBookAction(action: BookCardAction) {
    when (action) {
        is BookCardAction.BookDetails -> openBookDetail(action.bookId, BookDetailReturnTarget.Home)

        is BookCardAction.ReadingSessions,
        is BookCardAction.Author,
        is BookCardAction.Series,
        is BookCardAction.MakeAvailableOffline,
        is BookCardAction.RemoveDownload -> error(
            "Home emitted an unsupported Book action: $action"
        )
    }
}
