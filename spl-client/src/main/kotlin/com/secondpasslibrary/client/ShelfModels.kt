package com.secondpasslibrary.client

data class ShelfListOptions(
    val scope: ShelfScope = ShelfScope.ALL,
    val ownerGroupId: String? = null,
    val bookId: String? = null,
    val ordering: ShelfOrdering? = null,
    val page: Int = DEFAULT_SHELF_PAGE,
    val pageSize: Int = DEFAULT_SHELF_PAGE_SIZE,
    val previewLimit: Int = 0
) {
    init {
        validateShelfPage(page, pageSize)
        validateShelfPreviewLimit(previewLimit)
        require(ownerGroupId == null || ownerGroupId.isNotBlank()) {
            "Shelf owner-group ID must not be blank."
        }
        require(bookId == null || bookId.isNotBlank()) { "Shelf book ID must not be blank." }
    }
}

data class ShelfDetailOptions(val previewLimit: Int = 0) {
    init {
        validateShelfPreviewLimit(previewLimit)
    }
}

data class ShelfItemListOptions(
    val ordering: ShelfItemOrdering = ShelfItemOrdering.POSITION,
    val page: Int = DEFAULT_SHELF_PAGE,
    val pageSize: Int = DEFAULT_SHELF_PAGE_SIZE
) {
    init {
        validateShelfPage(page, pageSize)
    }
}

data class ShelfEditorListOptions(
    val page: Int = DEFAULT_SHELF_PAGE,
    val pageSize: Int = DEFAULT_SHELF_PAGE_SIZE
) {
    init {
        validateShelfPage(page, pageSize)
    }
}

data class CreatePersonalShelfInput(
    val name: String,
    val description: String = "",
    val visibility: ShelfVisibility = ShelfVisibility.PRIVATE
) {
    init {
        validateShelfName(name)
    }
}

data class UpdatePersonalShelfInput(
    val name: String? = null,
    val description: String? = null,
    val visibility: ShelfVisibility? = null
) {
    init {
        name?.let(::validateShelfName)
        require(name != null || description != null || visibility != null) {
            "Shelf update must contain at least one field."
        }
    }
}

data class AddShelfItemInput(val bookId: String, val position: Int? = null) {
    init {
        require(bookId.isNotBlank()) { "Shelf item Book ID must not be blank." }
        require(position == null || position >= 0) { "Shelf item position must not be negative." }
    }
}

enum class ShelfItemMove(internal val queryValue: String) {
    UP("up"),
    DOWN("down")
}

enum class ShelfScope(internal val queryValue: String) {
    ALL("all"),
    PERSONAL("personal"),
    SHARED("shared"),
    GROUP("group")
}

enum class ShelfOrdering(internal val queryValue: String) {
    NAME("name"),
    NAME_DESCENDING("-name"),
    ITEM_COUNT("item_count"),
    ITEM_COUNT_DESCENDING("-item_count")
}

enum class ShelfItemOrdering(internal val queryValue: String) {
    POSITION("position"),
    POSITION_DESCENDING("-position"),
    TITLE("title"),
    TITLE_DESCENDING("-title"),
    AUTHOR("author"),
    AUTHOR_DESCENDING("-author")
}

enum class ShelfVisibility {
    PRIVATE,
    LISTED
}

data class ShelfPage(
    val totalCount: Int,
    val hasNextPage: Boolean,
    val hasPreviousPage: Boolean,
    val shelves: List<Shelf>,
    val page: Int,
    val pageSize: Int
)

data class Shelf(
    val id: String,
    val name: String,
    val description: String?,
    val owner: ShelfOwner,
    val visibility: ShelfVisibility,
    val itemCount: Int,
    val canEdit: Boolean,
    val createdBy: ShelfUser?,
    val createdAt: String,
    val updatedAt: String,
    val matchedItemId: String?,
    val previewBooks: List<ShelfPreviewBook>?
)

sealed interface ShelfOwner {
    data class User(val profileId: String, val username: String?) : ShelfOwner

    data class Group(val id: String, val name: String, val isPublicGroup: Boolean) : ShelfOwner
}

data class ShelfUser(val profileId: String, val username: String?)

data class ShelfPreviewBook(val id: String, val title: String, val cover: PublicBookCoverReference?)

data class ShelfItemPage(
    val totalCount: Int,
    val results: List<ShelfItem>,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
    val page: Int,
    val pageSize: Int
)

data class ShelfItem(
    val id: String,
    val shelfId: String,
    val book: CompactBook,
    val position: Int,
    val addedBy: ShelfUser?,
    val createdAt: String,
    val updatedAt: String
)

data class ShelfEditorPage(
    val totalCount: Int,
    val visibleItemCount: Int,
    val unavailableItemCount: Int,
    val results: List<ShelfEditorItem>,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
    val page: Int,
    val pageSize: Int
)

sealed interface ShelfEditorItem {
    val id: String
    val shelfId: String
    val position: Int
    val addedBy: ShelfUser?

    data class Available(
        override val id: String,
        override val shelfId: String,
        override val position: Int,
        override val addedBy: ShelfUser?,
        val book: CompactBook,
        val createdAt: String,
        val updatedAt: String
    ) : ShelfEditorItem

    data class Unavailable(
        override val id: String,
        override val shelfId: String,
        override val position: Int,
        override val addedBy: ShelfUser?
    ) : ShelfEditorItem
}

/** Deliberately smaller Home-cache projection, not the complete Shelf contract. */
data class ShelfSummary(
    val id: String,
    val name: String,
    val description: String?,
    val owner: ShelfOwner,
    val visibility: ShelfVisibility,
    val itemCount: Int,
    val canEdit: Boolean,
    val previewBooks: List<ShelfPreviewBook>?
)

fun Shelf.toSummary(): ShelfSummary = ShelfSummary(
    id = id,
    name = name,
    description = description,
    owner = owner,
    visibility = visibility,
    itemCount = itemCount,
    canEdit = canEdit,
    previewBooks = previewBooks
)

private const val DEFAULT_SHELF_PAGE = 1
private const val DEFAULT_SHELF_PAGE_SIZE = 20
private const val MAX_SHELF_PAGE_SIZE = 200
private const val MAX_SHELF_PREVIEW_LIMIT = 24
private const val MAX_SHELF_NAME_LENGTH = 255

private fun validateShelfPage(page: Int, pageSize: Int) {
    require(page > 0) { "Shelf page must be positive." }
    require(pageSize in 1..MAX_SHELF_PAGE_SIZE) {
        "Shelf page size must be between 1 and $MAX_SHELF_PAGE_SIZE."
    }
}

private fun validateShelfPreviewLimit(previewLimit: Int) {
    require(previewLimit in 0..MAX_SHELF_PREVIEW_LIMIT) {
        "Shelf preview limit must be between 0 and $MAX_SHELF_PREVIEW_LIMIT."
    }
}

private fun validateShelfName(name: String) {
    val normalized = name.trim()
    require(normalized.isNotEmpty()) { "Shelf name must not be blank." }
    require(normalized.length <= MAX_SHELF_NAME_LENGTH) {
        "Shelf name must not exceed $MAX_SHELF_NAME_LENGTH characters."
    }
}
