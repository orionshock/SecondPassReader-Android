package com.secondpasslibrary.reader.settings

import com.secondpasslibrary.reader.app.storage.AccountLocalDownload
import com.secondpasslibrary.reader.app.storage.AccountLocalDownloadRepository
import com.secondpasslibrary.reader.app.storage.AccountLocalScope
import com.secondpasslibrary.reader.app.storage.OfflineBookAvailabilityController
import com.secondpasslibrary.reader.connection.AuthenticatedConnectionIdentity
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetResolver
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetStore
import com.secondpasslibrary.reader.reader.session.ReaderExistingSessionsCache
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsDownloadsViewModelTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `stale load cannot replace a newer refresh`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val account = AccountLocalScope.from("https://library.example", "profile-1")
            val firstLoad = CompletableDeferred<List<AccountLocalDownload>>()
            var calls = 0
            val repository = object : AccountLocalDownloadRepository {
                override suspend fun downloads(
                    account: AccountLocalScope
                ): List<AccountLocalDownload> {
                    calls++
                    return if (calls == 1) {
                        firstLoad.await()
                    } else {
                        listOf(AccountLocalDownload("new", "New", 4))
                    }
                }

                override suspend fun removeDownload(account: AccountLocalScope, bookId: String) {
                    // No mutation needed for this load-order test.
                }

                override suspend fun removeAllDownloads(account: AccountLocalScope) = Unit
            }
            val viewModel = SettingsDownloadsViewModel(repository, offlineController(repository))
            viewModel.initialize(account)
            runCurrent()
            viewModel.refresh()
            runCurrent()
            firstLoad.complete(listOf(AccountLocalDownload("old", "Old", 2)))
            advanceUntilIdle()
            assertEquals(listOf("new"), viewModel.state.value.downloads.map { it.bookId })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `loads account-scoped downloads and refreshes after bounded removal`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val account = AccountLocalScope.from("https://library.example", "profile-1")
            val other = AccountLocalScope.from("https://library.example", "profile-2")
            val repository = FakeDownloadsRepository()
            repository.books[account] = mutableListOf(
                AccountLocalDownload("book-1", "First", 12),
                AccountLocalDownload("book-2", "Second", 21)
            )
            repository.books[other] = mutableListOf(AccountLocalDownload("book-3", "Other", 4))
            val viewModel = SettingsDownloadsViewModel(repository, offlineController(repository))

            viewModel.initialize(account)
            advanceUntilIdle()
            assertEquals(2, viewModel.state.value.downloads.size)
            assertEquals(33L, viewModel.state.value.totalBytes)

            viewModel.remove("book-1")
            advanceUntilIdle()
            assertEquals(listOf("book-2"), viewModel.state.value.downloads.map { it.bookId })
            viewModel.clearBook(
                "book-2",
                AuthenticatedConnectionIdentity("https://library.example", "device")
            )
            advanceUntilIdle()
            assertEquals(emptyList<AccountLocalDownload>(), viewModel.state.value.downloads)
            viewModel.removeAll()
            advanceUntilIdle()
            assertFalse(viewModel.state.value.loading)
            assertEquals(emptyList<AccountLocalDownload>(), viewModel.state.value.downloads)
            assertEquals(listOf("book-3"), repository.books.getValue(other).map { it.bookId })
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun offlineController(repository: AccountLocalDownloadRepository) =
        OfflineBookAvailabilityController(
            resolver = ReaderBookAssetResolver { _, _ -> error("No download in this test") },
            assets = ReaderBookAssetStore.forTests(temporary.newFolder()),
            downloads = repository,
            sessions = ReaderExistingSessionsCache { _, _, _ -> },
            coverSource = com.secondpasslibrary.reader.app.storage.OfflineBookCoverSource {
                error("No cover in this test")
            }
        )

    private class FakeDownloadsRepository : AccountLocalDownloadRepository {
        val books = mutableMapOf<AccountLocalScope, MutableList<AccountLocalDownload>>()

        override suspend fun downloads(account: AccountLocalScope) =
            books[account].orEmpty().toList()

        override suspend fun removeDownload(account: AccountLocalScope, bookId: String) {
            books[account]?.removeAll { it.bookId == bookId }
        }

        override suspend fun removeAllDownloads(account: AccountLocalScope) {
            books[account]?.clear()
        }

        override suspend fun clearBook(
            account: AccountLocalScope,
            bookId: String,
            identity: AuthenticatedConnectionIdentity
        ) {
            books[account]?.removeAll { it.bookId == bookId }
        }
    }
}
