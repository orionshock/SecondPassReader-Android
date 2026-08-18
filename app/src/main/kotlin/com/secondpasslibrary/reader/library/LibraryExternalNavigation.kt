package com.secondpasslibrary.reader.library

internal sealed interface LibraryExternalNavigation {
    data class Author(val id: String) : LibraryExternalNavigation

    data class Series(val id: String) : LibraryExternalNavigation

    data class Tag(val id: String, val slug: String) : LibraryExternalNavigation
}
