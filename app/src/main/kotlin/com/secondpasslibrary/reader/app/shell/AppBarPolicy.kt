package com.secondpasslibrary.reader.app.shell

import com.secondpasslibrary.reader.design.components.AppBarNavigation
import com.secondpasslibrary.reader.design.components.AppBarPresentation

internal fun AppDestination.rootAppBarPresentation() =
    AppBarPresentation(AppBarNavigation.MENU, title = label)

internal fun BookDetailReturnTarget.appBarContextLabel(): String = when (this) {
    BookDetailReturnTarget.Home -> "Home"
    BookDetailReturnTarget.Library -> "Library"
    BookDetailReturnTarget.Marginalia -> "Marginalia"
    is BookDetailReturnTarget.ReadingSessionDetail -> "Reading session"
    is BookDetailReturnTarget.BookMarginalia -> "Reading sessions"
    is BookDetailReturnTarget.ShelfDetail -> "Shelves"
}
