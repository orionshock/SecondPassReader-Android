package com.secondpasslibrary.reader.reader.session

import com.secondpasslibrary.reader.connection.ConnectionProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSessionMetadataControllerTest {
    @Test
    fun `active current Session edits exact identity and publishes authoritative metadata`() =
        runTest {
            val writer = RecordingMetadataWriter()
            val updates = mutableListOf<ReaderSessionMetadata>()
            val controller = ReaderSessionMetadataController(writer, this, updates::add)
            controller.select(PROFILE, session(status = ReaderSessionStatus.ACTIVE))

            controller.beginEdit()
            controller.updateName("Evening read")
            controller.updateNotes("  first line\n  second line  ")
            controller.submit()
            advanceUntilIdle()

            assertEquals("session-1", writer.sessionId)
            assertEquals("Evening read", writer.name)
            assertEquals("  first line\n  second line  ", writer.notes)
            assertEquals(writer.result, controller.state.value.metadata)
            assertEquals(listOf(writer.result), updates)
            assertFalse(controller.state.value.editorOpen)
        }

    @Test
    fun `closed Session cannot open editor or write`() = runTest {
        val writer = RecordingMetadataWriter()
        val controller = ReaderSessionMetadataController(writer, this)
        controller.select(PROFILE, session(status = ReaderSessionStatus.CLOSED))

        controller.beginEdit()
        controller.updateName("Ignored")
        controller.submit()
        advanceUntilIdle()

        assertFalse(controller.state.value.editorOpen)
        assertEquals(0, writer.calls)
    }

    @Test
    fun `failure retains draft for explicit retry`() = runTest {
        val writer = RecordingMetadataWriter(failuresRemaining = 1)
        val controller = ReaderSessionMetadataController(writer, this)
        controller.select(PROFILE, session(status = ReaderSessionStatus.ACTIVE))
        controller.beginEdit()
        controller.updateName("Retry name")

        controller.submit()
        advanceUntilIdle()
        assertTrue(controller.state.value.failure)
        assertEquals("Retry name", controller.state.value.draftName)

        controller.submit()
        advanceUntilIdle()
        assertEquals(2, writer.calls)
        assertEquals("Retry name", controller.state.value.metadata?.name)
    }

    private class RecordingMetadataWriter(private var failuresRemaining: Int = 0) :
        ReaderSessionMetadataWriter {
        var calls = 0
        var sessionId: String? = null
        var name: String? = null
        var notes: String? = null
        var result = ReaderSessionMetadata("session-1", "Evening read", "authoritative notes")

        override suspend fun update(
            profile: ConnectionProfile,
            sessionId: String,
            name: String,
            notes: String
        ): ReaderSessionMetadata {
            calls += 1
            this.sessionId = sessionId
            this.name = name
            this.notes = notes
            if (failuresRemaining-- > 0) error("Unavailable")
            result = ReaderSessionMetadata(sessionId, name, notes)
            return result
        }
    }

    private companion object {
        val PROFILE = ConnectionProfile(
            serverOrigin = "https://library.example",
            serverBaseUrl = "https://library.example/",
            apiBaseUrl = "https://library.example/api/v1/",
            serverName = "Library",
            serverDescription = "",
            serverVersion = "1",
            serverReleaseDate = "2026-08-28",
            clientSessionId = "client-session",
            clientName = "Reader",
            clientType = "reader"
        )

        fun session(status: ReaderSessionStatus) = ReaderSessionContext(
            sessionId = "session-1",
            status = status,
            savedProgressCfi = null,
            sessionName = "Original",
            sessionNotes = "Original notes"
        )
    }
}
