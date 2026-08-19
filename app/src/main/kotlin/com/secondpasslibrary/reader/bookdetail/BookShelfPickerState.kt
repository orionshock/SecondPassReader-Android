package com.secondpasslibrary.reader.bookdetail

import com.secondpasslibrary.client.ShelfVisibility

internal data class BookShelfPickerState(
    val open: Boolean = false,
    val bookId: String? = null,
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val targets: List<BookShelfTarget> = emptyList(),
    val failure: BookShelfPickerFailure? = null
)

internal data class BookShelfTarget(
    val shelfId: String,
    val name: String,
    val itemCount: Int,
    val visibility: ShelfVisibility,
    val added: Boolean = false,
    val adding: Boolean = false,
    val failure: BookShelfPickerFailure? = null
)

internal enum class BookShelfPickerFailure {
    UNREACHABLE,
    AUTHENTICATION_REJECTED,
    PROTOCOL_INVALID,
    NOT_AUTHORIZED,
    NOT_FOUND,
    VALIDATION,
    OTHER
}
