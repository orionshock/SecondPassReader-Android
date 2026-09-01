package com.secondpasslibrary.reader.reader.annotations.bookmark

import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderVisiblePageBookmarksControllerTest {
    @Test
    fun `current bookmarks resolve and invalidation recomputes without historical contamination`() =
        runTest {
            val resolver = FakeResolver()
            val controller = ReaderVisiblePageBookmarksController(backgroundScope)
            val current = bookmark("current")

            controller.select("session", resolver)
            controller.replace("session", listOf(current, highlight(), bookmark("historical")))
            resolver.visibleIds = setOf("current")
            resolver.invalidate()
            runCurrent()

            assertEquals(listOf(current), controller.state.value.bookmarks)
            assertEquals(listOf("current", "historical"), resolver.requests.last().map { it.id })
        }

    @Test
    fun `stale resolver cannot publish after Session replacement`() = runTest {
        val first = FakeResolver()
        val second = FakeResolver().apply { visibleIds = setOf("new") }
        val controller = ReaderVisiblePageBookmarksController(backgroundScope)

        controller.select("old", first)
        controller.replace("old", listOf(bookmark("old")))
        controller.select("new-session", second)
        controller.replace("new-session", listOf(bookmark("new")))
        runCurrent()

        assertEquals(listOf("new"), controller.state.value.bookmarks.map { it.id })
    }

    @Test
    fun `annotations from a previous Session cannot replace current page state`() = runTest {
        val resolver = FakeResolver().apply { visibleIds = setOf("current", "previous") }
        val controller = ReaderVisiblePageBookmarksController(backgroundScope)

        controller.select("current-session", resolver)
        controller.replace("previous-session", listOf(bookmark("previous")))
        controller.replace("current-session", listOf(bookmark("current")))
        runCurrent()

        assertEquals(listOf("current"), controller.state.value.bookmarks.map { it.id })
    }
}

private class FakeResolver : ReaderVisiblePageBookmarksResolver {
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    var visibleIds: Set<String> = emptySet()
    val requests = mutableListOf<List<ReaderAnnotation.Bookmark>>()

    override fun invalidations(): Flow<Unit> = changes

    override suspend fun resolve(
        bookmarks: List<ReaderAnnotation.Bookmark>
    ): ReaderVisiblePageBookmarks {
        requests += bookmarks
        return ReaderVisiblePageBookmarks(bookmarks.filter { it.id in visibleIds })
    }

    fun invalidate() {
        changes.tryEmit(Unit)
    }
}

private fun bookmark(id: String) = ReaderAnnotation.Bookmark(
    id = id,
    clientId = "client-$id",
    cfi = "epubcfi(/6/2!/4/2:1)",
    locationLabel = "Chapter 01 · 1%",
    updatedAt = "now"
)

private fun highlight() = ReaderAnnotation.Highlight(
    id = "highlight",
    clientId = "highlight-client",
    cfi = "epubcfi(/6/2!/4/2:1,/1:0,/1:1)",
    locationLabel = null,
    updatedAt = "now",
    quote = "x",
    prefix = null,
    suffix = null,
    note = null,
    color = com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor.YELLOW
)
