package com.secondpasslibrary.reader.bookdetail

import com.secondpasslibrary.client.ShelfVisibility

internal fun BookShelfTarget.metadataLabel(): String {
    val count = "$itemCount ${if (itemCount == 1) "book" else "books"}"
    return "$count · ${visibility.label()}"
}

internal fun ShelfVisibility.label(): String = when (this) {
    ShelfVisibility.PRIVATE -> "Private"
    ShelfVisibility.LISTED -> "Listed"
}

internal fun BookShelfPickerFailure.message(): String = when (this) {
    BookShelfPickerFailure.UNREACHABLE -> "The server could not be reached."
    BookShelfPickerFailure.AUTHENTICATION_REJECTED -> "Your connection is no longer authorized."
    BookShelfPickerFailure.PROTOCOL_INVALID -> "The server returned an invalid response."
    BookShelfPickerFailure.NOT_AUTHORIZED -> "This shelf cannot be changed by this client."
    BookShelfPickerFailure.NOT_FOUND -> "That shelf is no longer available."
    BookShelfPickerFailure.VALIDATION -> "The server rejected this addition."
    BookShelfPickerFailure.OTHER -> "The book could not be added to this shelf."
}
