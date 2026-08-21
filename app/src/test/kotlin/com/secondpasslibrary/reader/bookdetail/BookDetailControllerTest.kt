package com.secondpasslibrary.reader.bookdetail

import com.secondpasslibrary.client.SplClientException
import com.secondpasslibrary.reader.library.axis.FakeLibraryAxisClient
import com.secondpasslibrary.reader.library.axis.FakeLibraryAxisClientProvider
import com.secondpasslibrary.reader.library.axis.libraryBookDetail
import com.secondpasslibrary.reader.library.axis.libraryProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookDetailControllerTest {
    @Test
    fun `shared controller loads Book Detail without feature controller dependencies`() = runTest {
        val client = FakeLibraryAxisClient()
        val controller = BookDetailController(FakeLibraryAxisClientProvider(client), this)
        controller.prepare(libraryProfile())

        controller.select("book-1")
        advanceUntilIdle()

        assertEquals("book-1", controller.state.value.detail?.id)
        assertEquals(listOf("book-1"), client.bookDetailRequests)
        val dependencies = BookDetailController::class.java.declaredConstructors
            .flatMap { it.parameterTypes.toList() }
            .map { it.name }
        assertEquals(false, dependencies.any { ".library.LibraryController" in it })
        assertEquals(false, dependencies.any { ".shelves.ShelvesController" in it })
    }

    @Test
    fun `failure is retained and retry loads the same Book`() = runTest {
        var attempts = 0
        val client = FakeLibraryAxisClient().apply {
            bookDetail = {
                attempts += 1
                if (attempts == 1) throw SplClientException.ProtocolInvalid("book")
                libraryBookDetail(it)
            }
        }
        val controller = BookDetailController(FakeLibraryAxisClientProvider(client), this)
        controller.prepare(libraryProfile())
        controller.select("book-1")
        advanceUntilIdle()

        assertEquals(BookDetailFailure.PROTOCOL_INVALID, controller.state.value.failure)

        controller.retry()
        advanceUntilIdle()
        assertEquals("book-1", controller.state.value.detail?.id)
        assertEquals(2, attempts)
    }
}
