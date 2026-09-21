package com.secondpasslibrary.reader.app.storage

import com.secondpasslibrary.client.PublicBookCoverReference
import com.secondpasslibrary.reader.app.AppAvailability
import com.secondpasslibrary.reader.app.AppAvailabilityReason
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.asset.ReaderAccountScope
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetChecksum
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetResolver
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetStore
import com.secondpasslibrary.reader.reader.asset.ReaderEpubIntegrityException
import com.secondpasslibrary.reader.reader.asset.ResolvedReaderBook
import com.secondpasslibrary.reader.reader.session.ReaderExistingSessionsCache
import java.security.MessageDigest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineBookAvailabilityControllerTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `download uses verified asset pipeline and never establishes a Session`() = runTest {
        val fixture = fixture()
        fixture.controller.makeAvailable(profile(), PROFILE_ID, BOOK_ID, AppAvailability.Online)
        assertTrue(fixture.controller.isAvailable(fixture.account, BOOK_ID))
        assertEquals(1, fixture.downloads)
        assertEquals(1, fixture.cachedSessions)
        assertEquals(1, fixture.controller.revision.value)

        fixture.controller.makeAvailable(profile(), PROFILE_ID, BOOK_ID, AppAvailability.Online)
        assertEquals(1, fixture.downloads)
        assertEquals(1, fixture.cachedSessions)
    }

    @Test
    fun `forced offline cannot fetch but leaves a verified local Book available`() = runTest {
        val fixture = fixture()
        val forced = AppAvailability.Offline(AppAvailabilityReason.USER_CHOICE)
        val failure = runCatching {
            fixture.controller.makeAvailable(profile(), PROFILE_ID, BOOK_ID, forced)
        }.exceptionOrNull()
        assertTrue(failure is BookDownloadNeedsLibraryException)
        assertFalse(fixture.controller.isAvailable(fixture.account, BOOK_ID))
        assertEquals(0, fixture.downloads)

        fixture.controller.makeAvailable(profile(), PROFILE_ID, BOOK_ID, AppAvailability.Online)
        fixture.controller.makeAvailable(profile(), PROFILE_ID, BOOK_ID, forced)
        assertTrue(fixture.controller.isAvailable(fixture.account, BOOK_ID))
        assertEquals(1, fixture.downloads)
    }

    @Test
    fun `download and retained cover survive a route change`() = runTest {
        val fixture = fixture(withCover = true)
        fixture.controller.makeAvailable(profile(), PROFILE_ID, BOOK_ID, AppAvailability.Online)
        val alternate = profile().copy(
            serverOrigin = "https://alternate.example",
            libraryBaseUrl = "https://alternate.example"
        )
        val sameAccount = AccountLocalScope.from(alternate.serverId, PROFILE_ID)

        assertTrue(fixture.controller.isAvailable(sameAccount, BOOK_ID))
        assertEquals(
            fixture.controller.localCover(fixture.account, BOOK_ID),
            fixture.controller.localCover(sameAccount, BOOK_ID)
        )
        fixture.controller.makeAvailable(alternate, PROFILE_ID, BOOK_ID, AppAvailability.Online)
        assertEquals(1, fixture.downloads)
    }

    @Test
    fun `bad checksum never becomes an offline asset`() = runTest {
        val fixture = fixture(corrupt = true)
        val failure = runCatching {
            fixture.controller.makeAvailable(profile(), PROFILE_ID, BOOK_ID, AppAvailability.Online)
        }.exceptionOrNull()
        assertTrue(failure is ReaderEpubIntegrityException)
        assertFalse(fixture.controller.isAvailable(fixture.account, BOOK_ID))
        assertEquals(0, fixture.cachedSessions)
    }

    @Test
    fun `download retains cover and existing asset backfills without another EPUB download`() =
        runTest {
            val fixture = fixture(withCover = true)
            fixture.controller.makeAvailable(profile(), PROFILE_ID, BOOK_ID, AppAvailability.Online)
            val firstCover = fixture.controller.localCover(fixture.account, BOOK_ID)
            assertTrue(firstCover?.isFile == true)
            assertTrue(firstCover!!.readBytes().contentEquals(COVER))
            assertEquals(1, fixture.coverFetches)
            firstCover.delete()

            fixture.controller.backfillCover(
                fixture.account,
                BOOK_ID,
                PublicBookCoverReference.fromAbsoluteUrl("https://library.example/cover.png")
            )
            assertEquals(1, fixture.resolves)
            assertEquals(1, fixture.downloads)
            assertEquals(2, fixture.coverFetches)
            assertTrue(fixture.controller.localCover(fixture.account, BOOK_ID)?.isFile == true)

            fixture.controller.remove(fixture.account, BOOK_ID)
            assertEquals(null, fixture.controller.localCover(fixture.account, BOOK_ID))
        }

    @Test
    fun `cover failure leaves verified EPUB available`() = runTest {
        val fixture = fixture(withCover = true, failCover = true)
        fixture.controller.makeAvailable(profile(), PROFILE_ID, BOOK_ID, AppAvailability.Online)
        assertTrue(fixture.controller.isAvailable(fixture.account, BOOK_ID))
        assertEquals(null, fixture.controller.localCover(fixture.account, BOOK_ID))
    }

    @Test
    fun `removal waits for a download and removes only completed asset metadata`() = runTest {
        val release = CompletableDeferred<Unit>()
        val fixture = fixture(release = release)
        val download = async {
            fixture.controller.makeAvailable(
                profile(),
                PROFILE_ID,
                BOOK_ID,
                AppAvailability.Online
            )
        }
        runCurrent()
        val removal = async { fixture.controller.remove(fixture.account, BOOK_ID) }
        runCurrent()
        release.complete(Unit)
        download.await()
        removal.await()
        assertFalse(fixture.controller.isAvailable(fixture.account, BOOK_ID))
        assertEquals(
            emptyList<AccountLocalDownload>(),
            fixture.repository.downloads(fixture.account)
        )
    }

    private fun fixture(
        corrupt: Boolean = false,
        release: CompletableDeferred<Unit>? = null,
        withCover: Boolean = false,
        failCover: Boolean = false
    ): Fixture {
        val store = ReaderBookAssetStore.forTests(temporary.newFolder())
        val account = AccountLocalScope.from(profile().serverId, PROFILE_ID)
        val repository = object : AccountLocalDownloadRepository {
            override suspend fun downloads(account: AccountLocalScope) =
                store.completedBooks(ReaderAccountScope.from(account)).map {
                    AccountLocalDownload(it.bookId, it.title, it.sizeBytes)
                }

            override suspend fun removeDownload(account: AccountLocalScope, bookId: String) =
                store.removeCompleted(ReaderAccountScope.from(account), bookId)

            override suspend fun removeAllDownloads(account: AccountLocalScope) =
                store.purgeAccount(ReaderAccountScope.from(account))
        }
        var downloads = 0
        var resolves = 0
        var coverFetches = 0
        var cachedSessions = 0
        val resolver = ReaderBookAssetResolver { request, _ ->
            resolves++
            val checksum = checksum(if (corrupt) "other".toByteArray() else EPUB)
            val asset = store.acquire(
                ReaderAccountScope(request.profile.serverId, request.profileId),
                request.bookId,
                checksum,
                onDownloadStarted = { downloads++ }
            ) { output ->
                release?.await()
                output.write(EPUB)
            }
            store.rememberCompletedBook(
                ReaderAccountScope(request.profile.serverId, request.profileId),
                request.bookId,
                "Book",
                checksum
            )
            ResolvedReaderBook(
                title = "Book",
                file = asset.file,
                reused = asset.reused,
                cover = if (withCover) {
                    PublicBookCoverReference.fromAbsoluteUrl("https://library.example/cover.png")
                } else {
                    null
                }
            )
        }
        val controller = OfflineBookAvailabilityController(
            resolver,
            store,
            repository,
            ReaderExistingSessionsCache { _, _, _ -> cachedSessions++ },
            OfflineBookCoverSource {
                coverFetches++
                if (failCover) throw IllegalStateException("cover failed")
                COVER
            }
        )
        return Fixture(
            controller,
            repository,
            account,
            { downloads },
            { cachedSessions },
            { resolves },
            { coverFetches }
        )
    }

    private data class Fixture(
        val controller: OfflineBookAvailabilityController,
        val repository: AccountLocalDownloadRepository,
        val account: AccountLocalScope,
        val downloadCount: () -> Int,
        val sessionCount: () -> Int,
        val resolveCount: () -> Int,
        val coverFetchCount: () -> Int
    ) {
        val downloads get() = downloadCount()
        val cachedSessions get() = sessionCount()
        val resolves get() = resolveCount()
        val coverFetches get() = coverFetchCount()
    }

    private fun checksum(bytes: ByteArray) = ReaderBookAssetChecksum.fromServer(
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
    )

    private fun profile() = ConnectionProfile(
        "a6722b5a-7982-4778-8c74-39be4241a654",
        ORIGIN, ORIGIN, "Library", "", "test", "2026-09-19",
        "session", "Tablet", "android"
    )

    private companion object {
        const val ORIGIN = "https://library.example"
        const val PROFILE_ID = "profile-1"
        const val BOOK_ID = "book-1"
        val EPUB = "valid immutable epub bytes".toByteArray()
        val COVER = "cover bytes".toByteArray()
    }
}
