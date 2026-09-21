package com.secondpasslibrary.reader.reader

import com.secondpasslibrary.reader.app.AppAvailability
import com.secondpasslibrary.reader.app.AppAvailabilityReason
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.asset.ReaderAccountScope
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetChecksum
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetStore
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderLaunchPolicyTest {
    @Test
    fun `online launch keeps existing network path without a local EPUB`() = runTest {
        val policy = policy()

        assertEquals(
            ReaderLaunchDecision.ONLINE,
            policy.decide(AppAvailability.Online, profile(), "profile-1", "book-1")
        )
    }

    @Test
    fun `offline launch allows only exact account completed EPUB`() = runTest {
        val root = Files.createTempDirectory("reader-launch").toFile()
        val store = ReaderBookAssetStore.forTests(root)
        val account = ReaderAccountScope(profile().serverId, "profile-1")
        val checksum = checksum(EPUB_BYTES)
        store.acquire(
            account,
            "book-1",
            checksum,
            {}
        ) { it.write(EPUB_BYTES) }
        store.rememberCompletedBook(account, "book-1", "Book", checksum)
        val policy = ReaderLaunchPolicy(store)
        val offline = AppAvailability.Offline(AppAvailabilityReason.UNREACHABLE)

        assertEquals(
            ReaderLaunchDecision.LOCAL_AVAILABLE,
            policy.decide(offline, profile(), "profile-1", "book-1")
        )
        assertEquals(
            ReaderLaunchDecision.OFFLINE_ASSET_UNAVAILABLE,
            policy.decide(offline, profile(), "profile-2", "book-1")
        )
    }

    @Test
    fun `offline launch rejects corrupt completed EPUB`() = runTest {
        val root = Files.createTempDirectory("reader-launch-corrupt").toFile()
        val store = ReaderBookAssetStore.forTests(root)
        val account = ReaderAccountScope(profile().serverId, "profile-1")
        val checksum = checksum(EPUB_BYTES)
        store.acquire(account, "book-1", checksum, {}) { it.write(EPUB_BYTES) }
        store.rememberCompletedBook(account, "book-1", "Book", checksum)
        store.completedFile(account, "book-1").writeBytes("damaged".toByteArray())

        assertEquals(
            ReaderLaunchDecision.OFFLINE_ASSET_UNAVAILABLE,
            ReaderLaunchPolicy(store).decide(
                AppAvailability.Offline(AppAvailabilityReason.UNREACHABLE),
                profile(),
                "profile-1",
                "book-1"
            )
        )
    }

    private fun policy() = ReaderLaunchPolicy(
        ReaderBookAssetStore.forTests(Files.createTempDirectory("reader-launch").toFile())
    )

    private fun profile() = ConnectionProfile(
        serverId = "a6722b5a-7982-4778-8c74-39be4241a654",
        serverOrigin = "https://library.example",
        libraryBaseUrl = "https://library.example",
        serverName = "Library",
        serverDescription = "",
        serverVersion = "1",
        serverReleaseDate = "2026-08-23",
        clientSessionId = "session-1",
        clientName = "Reader",
        clientType = "reader"
    )

    private fun checksum(bytes: ByteArray): ReaderBookAssetChecksum {
        val value = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
        return ReaderBookAssetChecksum.fromServer(value)
    }

    private companion object {
        val EPUB_BYTES = "epub".toByteArray()
    }
}
