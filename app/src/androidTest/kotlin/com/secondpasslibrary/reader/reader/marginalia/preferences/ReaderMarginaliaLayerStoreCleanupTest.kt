package com.secondpasslibrary.reader.reader.marginalia.preferences

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.secondpasslibrary.reader.connection.AuthenticatedConnectionIdentity
import java.io.File
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderMarginaliaLayerStoreCleanupTest {
    @Test
    fun bookCleanupRemovesOnlyItsSessionVisibilityAndKeepsDevicePreference() = runBlocking {
        val context = isolatedTestContext()
        val store = DataStoreReaderMarginaliaLayerStore(context)
        val identity = AuthenticatedConnectionIdentity(
            "https://library.example/api/v1/",
            "client-session"
        )
        val target = ReaderMarginaliaVisibilityScope(identity, "book-target")
        val kept = ReaderMarginaliaVisibilityScope(identity, "book-kept")
        val now = Instant.now()
        store.writeAutoShowPrevious(false)
        store.write(target, "session-target", visible = false, touchedAt = now)
        store.write(kept, "session-kept", visible = true, touchedAt = now)

        store.clearBookState(target, listOf("session-target"))
        store.clearBookState(target, listOf("session-target"))

        assertNull(store.read(target, "session-target", now))
        assertEquals(true, store.read(kept, "session-kept", now))
        assertFalse(store.readAutoShowPrevious())
    }

    @Test
    fun cleanupRemovesSessionVisibilityAndPreservesDevicePreference() = runBlocking {
        val context = isolatedTestContext()
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

    private fun isolatedTestContext(): Context =
        object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getApplicationContext(): Context = this

            override fun getFilesDir(): File =
                File(baseContext.filesDir, "reader-marginalia-test").apply { mkdirs() }
        }
}
