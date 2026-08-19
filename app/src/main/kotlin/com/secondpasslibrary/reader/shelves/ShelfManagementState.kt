package com.secondpasslibrary.reader.shelves

import com.secondpasslibrary.client.ShelfVisibility

internal data class EditPersonalShelfState(
    val shelfId: String? = null,
    val name: String = "",
    val description: String = "",
    val visibility: ShelfVisibility = ShelfVisibility.PRIVATE,
    val originalName: String = "",
    val originalDescription: String = "",
    val originalVisibility: ShelfVisibility = ShelfVisibility.PRIVATE,
    val submitting: Boolean = false,
    val nameError: ShelfMetadataFieldError? = null,
    val descriptionError: ShelfMetadataFieldError? = null,
    val visibilityError: ShelfMetadataFieldError? = null,
    val failure: ShelfManagementFailure? = null
) {
    val open: Boolean
        get() = shelfId != null

    val changed: Boolean
        get() =
            name.trim() != originalName ||
                description.trim() != originalDescription ||
                visibility != originalVisibility
}

internal data class DeletePersonalShelfState(
    val shelfId: String? = null,
    val shelfName: String = "",
    val deleting: Boolean = false,
    val failure: ShelfManagementFailure? = null
) {
    val open: Boolean
        get() = shelfId != null
}

internal enum class ShelfMetadataFieldError {
    REQUIRED,
    TOO_LONG,
    SERVER_REJECTED
}

internal enum class ShelfManagementFailure {
    UNREACHABLE,
    AUTHENTICATION_REJECTED,
    VALIDATION,
    NOT_AUTHORIZED,
    NOT_FOUND,
    REJECTED,
    OTHER
}
