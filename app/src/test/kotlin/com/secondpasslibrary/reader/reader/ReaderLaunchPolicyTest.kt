package com.secondpasslibrary.reader.reader

import com.secondpasslibrary.reader.app.AppAvailability
import com.secondpasslibrary.reader.app.AppAvailabilityReason
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.asset.ReaderAccountScope
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetStore
import java.nio.file.Files
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
        store.acquire(
            ReaderAccountScope("https://library.example", "profile-1"),
            "book-1",
            {}
        ) { it.write("epub".toByteArray()) }
        val policy = ReaderLaunchPolicy(store)
        val offline = AppAvailability.Offline(AppAvailabilityReason.UNREACHABLE)

        assertEquals(
            ReaderLaunchDecision.LOCAL_READ_ONLY,
            policy.decide(offline, profile(), "profile-1", "book-1")
        )
        assertEquals(
            ReaderLaunchDecision.OFFLINE_ASSET_UNAVAILABLE,
            policy.decide(offline, profile(), "profile-2", "book-1")
        )
    }

    private fun policy() = ReaderLaunchPolicy(
        ReaderBookAssetStore.forTests(Files.createTempDirectory("reader-launch").toFile())
    )

    private fun profile() = ConnectionProfile(
        serverOrigin = "https://library.example",
        serverBaseUrl = "https://library.example/",
        apiBaseUrl = "https://library.example/api/v1/",
        serverName = "Library",
        serverDescription = "",
        serverVersion = "1",
        serverReleaseDate = "2026-08-23",
        clientSessionId = "session-1",
        clientName = "Reader",
        clientType = "reader"
    )
}
