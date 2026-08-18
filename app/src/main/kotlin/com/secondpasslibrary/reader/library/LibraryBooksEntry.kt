package com.secondpasslibrary.reader.library

internal sealed interface LibraryBooksEntry {
    data object Browse : LibraryBooksEntry

    data class BroadSearch(val query: String) : LibraryBooksEntry
}
