package com.secondpasslibrary.reader.bookdetail.shelfpicker

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
    BookShelfPickerFailure.UNREACHABLE ->
        "Couldn’t reach the Library. Check your connection and retry."

    BookShelfPickerFailure.AUTHENTICATION_REJECTED ->
        "Your connection is no longer authorized. Repair it in Settings."

    BookShelfPickerFailure.PROTOCOL_INVALID ->
        "Couldn’t read the Library response. Retry or repair the connection in Settings."

    BookShelfPickerFailure.NOT_AUTHORIZED -> "You can’t change this Shelf."

    BookShelfPickerFailure.NOT_FOUND ->
        "This Shelf is no longer available. Retry to refresh Shelves."

    BookShelfPickerFailure.VALIDATION ->
        "This Book can’t be added to the Shelf. Choose another Shelf."

    BookShelfPickerFailure.OTHER -> "Couldn’t add the Book to this Shelf. Retry."
}
