package com.secondpasslibrary.reader.reader.asset

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.secondpasslibrary.reader.live.LiveServerTest
import com.secondpasslibrary.reader.reader.readium.cfi.RealSplCfiInteropEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.io.RandomAccessFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val REAL_SPL_INTEGRITY_ARGUMENT = "reader.realSplIntegrity"

/** Destructive-to-local-copy, opt-in proof against the paired development account. */
@LiveServerTest
@RunWith(AndroidJUnit4::class)
class RealSplEpubIntegrityTest {
    @Test
    fun corruptDownloadedEpubIsRejectedThenReplacedFromServer() = runBlocking {
        assumeTrue("Real SPL integrity proof was not requested.", isRealIntegrityEnabled())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val owners = context.realSplIntegrityOwners()
        val profile = owners.connectionProfileStore().read()
        val persistedAccount = owners.persistedAccountContextStore().read()
        assumeNotNull(profile, persistedAccount)
        requireNotNull(profile)
        requireNotNull(persistedAccount)
        assumeTrue(
            "The paired account descriptor does not match the connection.",
            persistedAccount.matches(profile)
        )

        val client = owners.authenticatedClientProvider().forProfile(profile)
        val compactBook = client.library.books.list().results.firstOrNull {
            it.fileFormat.equals("epub", ignoreCase = true)
        }
        assumeNotNull(compactBook)
        requireNotNull(compactBook)
        val detail = client.library.books.getBook(compactBook.id)
        val checksum = ReaderBookAssetChecksum.fromServer(detail.file?.checksum)
        val request = ReaderBookAssetRequest(profile, persistedAccount.profileId, detail.id)
        val account = ReaderAccountScope(profile.serverOrigin, persistedAccount.profileId)

        val downloaded = owners.readerBookAssetResolver().resolve(request) {}
        assertTrue(checksum.matches(downloaded.file))
        assertTrue(
            owners.readerBookAssetStore().completedBooks(account).any { it.bookId == detail.id }
        )

        RandomAccessFile(downloaded.file, "rw").use { file ->
            val firstByte = file.read()
            require(firstByte >= 0)
            file.seek(0)
            file.write(firstByte xor 0xff)
        }
        assertFalse(checksum.matches(downloaded.file))

        val offlineAttempt = runCatching {
            owners.readerBookAssetResolver().resolve(request.copy(localOnly = true)) {}
        }
        assertTrue(offlineAttempt.exceptionOrNull() is ReaderEpubUnavailableException)
        assertFalse(downloaded.file.exists())
        assertFalse(
            owners.readerBookAssetStore().completedBooks(account).any { it.bookId == detail.id }
        )

        val restored = owners.readerBookAssetResolver().resolve(request) {}
        assertFalse(restored.reused)
        assertTrue(checksum.matches(restored.file))
        assertTrue(
            owners.readerBookAssetStore().completedBooks(account).any { it.bookId == detail.id }
        )
    }
}

private fun isRealIntegrityEnabled(): Boolean =
    InstrumentationRegistry.getArguments().getString(REAL_SPL_INTEGRITY_ARGUMENT).toBoolean()

private fun Context.realSplIntegrityOwners(): RealSplCfiInteropEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, RealSplCfiInteropEntryPoint::class.java)
