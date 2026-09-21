package com.secondpasslibrary.reader.connection

import com.secondpasslibrary.client.AuthenticatedSecondPassClient
import com.secondpasslibrary.client.AuthenticatedSecondPassClientFactory
import com.secondpasslibrary.client.BearerCredential
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

internal class StoredAuthenticatedClientProviderTest : ConnectionCoordinatorTestSupport() {
    @Test
    fun `feature client uses active route without changing saved profile identity`() = runTest {
        val original = profile()
        val active = "https://library-alt.example"
        val routes = FakeRoutesStore().apply {
            stored = KnownServerRoutes(
                original.serverId,
                listOf(original.libraryBaseUrl, active),
                active
            )
        }
        var selected: String? = null
        val factory = object : AuthenticatedSecondPassClientFactory {
            override fun authenticated(
                libraryBaseUrl: String,
                credential: BearerCredential
            ): AuthenticatedSecondPassClient {
                selected = libraryBaseUrl
                error("route captured")
            }
        }
        val provider = StoredAuthenticatedClientProvider(factory, storedCredential(), routes)

        runCatching { provider.forProfile(original) }

        assertEquals(active, selected)
        assertEquals("https://library.example", original.libraryBaseUrl)
    }
}
