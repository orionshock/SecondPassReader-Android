package com.secondpasslibrary.client

data class ShelfListOptions(
    val page: Int? = null,
    val pageSize: Int? = null,
    val ordering: ShelfOrdering? = null,
    val previewLimit: Int? = null
) {
    init {
        require(page == null || page > 0) { "Shelf page must be positive." }
        require(pageSize == null || pageSize > 0) { "Shelf page size must be positive." }
        require(previewLimit == null || previewLimit > 0) {
            "Shelf preview limit must be positive."
        }
    }
}

enum class ShelfOrdering(internal val queryValue: String) {
    NAME("name"),
    NAME_DESCENDING("-name"),
    ITEM_COUNT("item_count"),
    ITEM_COUNT_DESCENDING("-item_count")
}

data class ShelfPage(
    val totalCount: Int,
    val hasNextPage: Boolean,
    val hasPreviousPage: Boolean,
    val shelves: List<ShelfSummary>
)

data class ShelfSummary(
    val id: String,
    val name: String,
    val description: String?,
    val owner: ShelfOwner,
    val visibility: String,
    val itemCount: Int,
    val canEdit: Boolean,
    val previewBooks: List<ShelfPreviewBook>?
)

sealed interface ShelfOwner {
    data class User(
        val profileId: String,
        val username: String?,
        val firstName: String?,
        val lastName: String?
    ) : ShelfOwner

    data class Group(val id: String, val name: String, val isPublicGroup: Boolean?) : ShelfOwner

    data class Other(val type: String) : ShelfOwner
}

data class ShelfPreviewBook(val id: String, val title: String, val cover: PublicBookCoverReference?)
