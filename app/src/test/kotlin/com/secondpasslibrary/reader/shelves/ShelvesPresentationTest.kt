package com.secondpasslibrary.reader.shelves

import com.secondpasslibrary.client.Shelf
import com.secondpasslibrary.client.ShelfOwner
import com.secondpasslibrary.client.ShelfPreviewBook
import com.secondpasslibrary.client.ShelfVisibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelvesPresentationTest {
    @Test
    fun `personal Shared-user and Group ownership stay distinct`() {
        val personal = shelf(ShelfOwner.User("profile", "reader"), canEdit = true)
        val shared = shelf(ShelfOwner.User("other", "alex"))
        val group = shelf(ShelfOwner.Group("group", "Common Room", true))

        assertEquals(ShelfOwnerKind.PERSONAL, personal.toCardPresentation().ownerKind)
        assertEquals("My shelf", personal.toCardPresentation().ownerLabel)
        assertEquals(ShelfOwnerKind.SHARED_USER, shared.toCardPresentation().ownerKind)
        assertEquals("Shared by @alex", shared.toCardPresentation().ownerLabel)
        assertEquals(ShelfOwnerKind.GROUP, group.toCardPresentation().ownerKind)
        assertEquals("Common Room", group.toCardPresentation().ownerLabel)
    }

    @Test
    fun `listed visibility is not presented as Public Group ownership`() {
        val model =
            shelf(
                ShelfOwner.User("other", "alex"),
                visibility = ShelfVisibility.LISTED
            ).toCardPresentation()

        assertEquals("Listed", model.visibilityLabel)
        assertEquals(ShelfOwnerKind.SHARED_USER, model.ownerKind)
        assertFalse(model.canEdit)
    }

    @Test
    fun `preview absence empty and populated remain distinguishable`() {
        val absent = shelf(previews = null).toCardPresentation().previews
        val empty = shelf(previews = emptyList()).toCardPresentation().previews
        val populated =
            shelf(
                previews =
                    listOf(
                        ShelfPreviewBook("one", "One", null),
                        ShelfPreviewBook("two", "Two", null),
                        ShelfPreviewBook("three", "Three", null),
                        ShelfPreviewBook("four", "Four", null)
                    )
            ).toCardPresentation().previews

        assertTrue(absent is ShelfPreviewPresentation.Absent)
        assertTrue(empty is ShelfPreviewPresentation.Empty)
        assertEquals(3, (populated as ShelfPreviewPresentation.Books).books.size)
    }

    @Test
    fun `book count ordering labels and paging threshold are explicit`() {
        assertEquals("1 book", shelf(itemCount = 1).toCardPresentation().itemCountLabel)
        assertEquals("2 books", shelf(itemCount = 2).toCardPresentation().itemCountLabel)
        assertEquals("Name A-Z", shelfOrderingOptions.first().label)
        assertEquals("Shelf order", shelfItemOrderingOptions.first().label)
        assertFalse(shouldRequestShelfNextPage(2, 10, prefetchDistance = 3))
        assertTrue(shouldRequestShelfNextPage(7, 10, prefetchDistance = 3))
    }

    private fun shelf(
        owner: ShelfOwner = ShelfOwner.User("profile", "reader"),
        canEdit: Boolean = false,
        visibility: ShelfVisibility = ShelfVisibility.PRIVATE,
        itemCount: Int = 0,
        previews: List<ShelfPreviewBook>? = emptyList()
    ) = Shelf(
        id = "shelf",
        name = "Shelf",
        description = null,
        owner = owner,
        visibility = visibility,
        itemCount = itemCount,
        canEdit = canEdit,
        createdBy = null,
        createdAt = "2026-08-18T00:00:00Z",
        updatedAt = "2026-08-18T00:00:00Z",
        matchedItemId = null,
        previewBooks = previews
    )
}
