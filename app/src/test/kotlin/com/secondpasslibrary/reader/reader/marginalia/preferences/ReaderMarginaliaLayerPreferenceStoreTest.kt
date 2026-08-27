package com.secondpasslibrary.reader.reader.marginalia.preferences

import com.secondpasslibrary.reader.connection.AuthenticatedConnectionIdentity
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderMarginaliaLayerPreferenceStoreTest {
    @Test
    fun `visibility expires at ninety days`() {
        val now = Instant.parse("2026-08-26T12:00:00Z")

        assertTrue(isReaderMarginaliaVisibilityFresh(now.minusSeconds(DAY_SECONDS * 89), now))
        assertFalse(isReaderMarginaliaVisibilityFresh(now.minusSeconds(DAY_SECONDS * 90), now))
        assertFalse(isReaderMarginaliaVisibilityFresh(now.plusSeconds(1), now))
    }

    @Test
    fun `cache identity includes server account Book and Session`() {
        val base = scope("https://one.example/api/", "account-a", "book-a")
        val key = readerMarginaliaVisibilityCacheKey(base, "session-a")

        assertNotEquals(
            key,
            readerMarginaliaVisibilityCacheKey(
                scope("https://two.example/api/", "account-a", "book-a"),
                "session-a"
            )
        )
        assertNotEquals(
            key,
            readerMarginaliaVisibilityCacheKey(
                scope("https://one.example/api/", "account-b", "book-a"),
                "session-a"
            )
        )
        assertNotEquals(
            key,
            readerMarginaliaVisibilityCacheKey(
                scope("https://one.example/api/", "account-a", "book-b"),
                "session-a"
            )
        )
        assertNotEquals(key, readerMarginaliaVisibilityCacheKey(base, "session-b"))
    }

    @Test
    fun `fresh install auto show default is enabled`() {
        assertTrue(DEFAULT_AUTO_SHOW_PREVIOUS)
    }

    private fun scope(server: String, account: String, bookId: String) =
        ReaderMarginaliaVisibilityScope(
            AuthenticatedConnectionIdentity(server, account),
            bookId
        )

    private companion object {
        const val DAY_SECONDS = 86_400L
    }
}
