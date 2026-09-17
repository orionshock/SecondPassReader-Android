package com.secondpasslibrary.reader.reader.marginalia.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.secondpasslibrary.reader.connection.AuthenticatedConnectionIdentity
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderMarginaliaLayerStoreCleanupTest {
    @Test
    fun cleanupRemovesSessionVisibilityAndPreservesDevicePreference() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = DataStoreReaderMarginaliaLayerStore(context)
        val scope = ReaderMarginaliaVisibilityScope(
            AuthenticatedConnectionIdentity(
                "https://library.example/api/v1/",
                "client-session"
            ),
            "book-1"
        )
        val now = Instant.parse("2026-09-16T12:00:00Z")
        store.writeAutoShowPrevious(false)
        store.write(scope, "reading-session", visible = false, touchedAt = now)

        store.clearAccountState()

        assertFalse(store.readAutoShowPrevious())
        assertNull(store.read(scope, "reading-session", now))
    }
}
