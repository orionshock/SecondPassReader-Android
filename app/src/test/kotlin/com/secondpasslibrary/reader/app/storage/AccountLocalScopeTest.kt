package com.secondpasslibrary.reader.app.storage

import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.home.projection.HomeAccountScopeKey
import com.secondpasslibrary.reader.reader.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.asset.ReaderAccountScope
import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetStore
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountLocalScopeTest {
    private val serverId = "a6722b5a-7982-4778-8c74-39be4241a654"

    @Test
    fun `server and profile define the shared local namespace`() {
        val canonical = AccountLocalScope.from(serverId.uppercase(), " profile-1 ")
        val same = AccountLocalScope.from(serverId, "profile-1")

        assertEquals(same, canonical)
        assertEquals(same.storageKey, HomeAccountScopeKey.from(same).value)
        assertEquals(same.storageKey, LocalReaderAccountKey.from(same).value)
        assertEquals(same, ReaderAccountScope.from(same).account)
        assertNotEquals(same, AccountLocalScope.from(serverId, "profile-2"))
        assertNotEquals(
            same,
            AccountLocalScope.from("b6722b5a-7982-4778-8c74-39be4241a654", "profile-1")
        )
    }

    @Test
    fun `route change keeps asset path Reader namespace and Home namespace`() {
        val original = profile("https://library.example")
        val alternate = original.copy(
            serverOrigin = "https://alternate.example",
            libraryBaseUrl = "https://alternate.example"
        )
        val originalScope = AccountLocalScope.from(original.serverId, "profile-1")
        val alternateScope = AccountLocalScope.from(alternate.serverId, "profile-1")
        val assets = ReaderBookAssetStore.forTests(
            Files.createTempDirectory("route-assets").toFile()
        )

        assertEquals(originalScope, alternateScope)
        assertEquals(
            assets.completedFile(ReaderAccountScope.from(originalScope), "book-1"),
            assets.completedFile(ReaderAccountScope.from(alternateScope), "book-1")
        )
        assertEquals(
            LocalReaderAccountKey.from(originalScope),
            LocalReaderAccountKey.from(alternateScope)
        )
        assertEquals(
            HomeAccountScopeKey.from(originalScope),
            HomeAccountScopeKey.from(alternateScope)
        )
    }

    @Test
    fun `URL cannot become an account-local server identity`() {
        assertThrows(IllegalArgumentException::class.java) {
            AccountLocalScope.from("https://library.example", "profile-1")
        }
    }

    private fun profile(url: String) = ConnectionProfile(
        serverId, url, url, "Library", "", "1", "", "session", "Tablet", "android"
    )
}
