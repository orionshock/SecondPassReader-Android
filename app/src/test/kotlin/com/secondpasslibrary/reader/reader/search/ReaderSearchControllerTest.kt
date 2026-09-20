package com.secondpasslibrary.reader.reader.search

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSearchControllerTest {
    @Test
    fun `blank query does not search and close clears ephemeral state`() = runTest {
        val fake = FakeSearch()
        val controller = ReaderSearchController(fake, backgroundScope, 300)
        controller.open()
        controller.query("  ")
        advanceTimeBy(400)
        runCurrent()
        assertEquals(emptyList<String>(), fake.queries)
        assertFalse(controller.state.value.loading)
        controller.close()
        assertEquals(ReaderSearchState(), controller.state.value)
    }

    @Test
    fun `trimmed query loads results and keeps search open after navigation`() = runTest {
        val fake = FakeSearch()
        val result = result("found")
        fake.pages = listOf(listOf(result))
        val controller = ReaderSearchController(fake, backgroundScope, 300)
        controller.open()
        controller.query("  found  ")
        advanceTimeBy(300)
        runCurrent()
        assertEquals(listOf("found"), fake.queries)
        assertEquals(listOf(result), controller.state.value.results)
        controller.select(result)
        runCurrent()
        assertTrue(controller.state.value.active)
        assertEquals(result.target, controller.state.value.selected)
        assertEquals(listOf(result.target), fake.navigated)
    }

    @Test
    fun `next page waits for demand`() = runTest {
        val fake = FakeSearch()
        fake.pages = listOf(listOf(result("one")), listOf(result("two")))
        val controller = ReaderSearchController(fake, backgroundScope, 0)
        controller.open()
        controller.query("word")
        runCurrent()
        assertEquals(1, controller.state.value.results.size)
        controller.loadMore()
        runCurrent()
        assertEquals(2, controller.state.value.results.size)
    }

    @Test
    fun `no results and errors remain local to search`() = runTest {
        val fake = FakeSearch()
        val controller = ReaderSearchController(fake, backgroundScope, 0)
        controller.open()
        controller.query("missing")
        runCurrent()
        assertTrue(controller.state.value.results.isEmpty())
        assertFalse(controller.state.value.error)
        fake.fails = true
        controller.query("broken")
        runCurrent()
        assertTrue(controller.state.value.error)
        assertTrue(controller.state.value.active)
        fake.fails = false
        controller.query("broken")
        runCurrent()
        assertFalse(controller.state.value.error)
    }

    @Test
    fun `new query and close invalidate an unfinished local search`() = runTest {
        val fake = FakeSearch()
        val gate = CompletableDeferred<Unit>()
        fake.gate = gate
        val controller = ReaderSearchController(fake, backgroundScope, 0)
        controller.open()
        controller.query("old")
        runCurrent()
        controller.query("new")
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf("new"), controller.state.value.results.map { it.match })
        controller.close()
        assertEquals(ReaderSearchState(), controller.state.value)
    }

    private class FakeSearch : ReaderBookSearch {
        val queries = mutableListOf<String>()
        val navigated = mutableListOf<ReaderSearchTarget>()
        var pages: List<List<ReaderSearchResult>> = emptyList()
        var fails = false
        var gate: CompletableDeferred<Unit>? = null

        override fun search(query: String): Flow<List<ReaderSearchResult>> = flow {
            queries += query
            if (query == "old") gate?.await()
            if (fails) error("search failure")
            if (query == "new") {
                emit(listOf(result("new")))
            } else {
                pages.forEach { emit(it) }
            }
        }

        override suspend fun goTo(target: ReaderSearchTarget): Boolean {
            navigated += target
            return true
        }
    }
}

private fun result(match: String) = ReaderSearchResult(
    target = object : ReaderSearchTarget {},
    before = "text before",
    match = match,
    after = "text after",
    title = null
)
