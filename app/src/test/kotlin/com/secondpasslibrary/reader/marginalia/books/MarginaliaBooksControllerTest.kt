package com.secondpasslibrary.reader.marginalia.books

import com.secondpasslibrary.reader.marginalia.RecordingMarginaliaCapability
import com.secondpasslibrary.reader.marginalia.marginaliaBook
import com.secondpasslibrary.reader.marginalia.marginaliaPage
import com.secondpasslibrary.reader.marginalia.marginaliaProfile
import com.secondpasslibrary.reader.marginalia.marginaliaProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MarginaliaBooksControllerTest {
    @Test
    fun `initial load and committed search use Marginalia Books capability`() = runTest {
        val capability = RecordingMarginaliaCapability().apply {
            marginaliaBooksCall = { options ->
                marginaliaPage(options.page, listOf(marginaliaBook("book-${options.page}")))
            }
        }
        val controller = MarginaliaBooksController(marginaliaProvider(capability), this)

        controller.prepare(marginaliaProfile())
        controller.enter()
        advanceUntilIdle()
        controller.commitSearch("  Dresden  ")
        advanceUntilIdle()

        assertEquals(listOf(null, "Dresden"), capability.marginaliaBookRequests.map { it.q })
        assertEquals("Dresden", controller.state.value.committedQuery)
        assertEquals(listOf("book-1"), controller.state.value.books.map { it.id })
    }

    @Test
    fun `enter preserves loaded Books state`() = runTest {
        val capability = RecordingMarginaliaCapability().apply {
            marginaliaBooksCall = { marginaliaPage(it.page, listOf(marginaliaBook("book-1"))) }
        }
        val controller = MarginaliaBooksController(marginaliaProvider(capability), this)

        controller.prepare(marginaliaProfile())
        controller.enter()
        advanceUntilIdle()
        controller.enter()
        advanceUntilIdle()

        assertEquals(1, capability.marginaliaBookRequests.size)
        assertFalse(controller.state.value.initialLoading)
    }
}
