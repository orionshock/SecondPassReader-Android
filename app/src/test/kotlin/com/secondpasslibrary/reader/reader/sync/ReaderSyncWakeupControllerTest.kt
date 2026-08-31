package com.secondpasslibrary.reader.reader.sync

import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.reader.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.ReaderPendingSyncScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSyncWakeupControllerTest {
    @Test
    fun `restored account shell repairs one durable pending-work wakeup`() = runTest {
        val scheduler = RecordingScheduler()
        val controller = ReaderSyncWakeupController(scheduler, this)

        controller.update(profile(), "profile-1")
        controller.update(profile(), "profile-1")
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(account("profile-1")), scheduler.scheduled)
    }

    @Test
    fun `account switch schedules only the exact new account scope`() = runTest {
        val scheduler = RecordingScheduler()
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val controller = ReaderSyncWakeupController(scheduler, scope)

        controller.update(profile(), "profile-1")
        testScheduler.advanceUntilIdle()
        controller.update(profile(), "profile-2")
        testScheduler.advanceUntilIdle()

        assertEquals(
            listOf(account("profile-1"), account("profile-2")),
            scheduler.scheduled
        )
    }

    private class RecordingScheduler : ReaderPendingSyncScheduler {
        val scheduled = mutableListOf<LocalReaderAccountKey>()

        override suspend fun ensureEnqueued(account: LocalReaderAccountKey) {
            scheduled += account
        }

        override fun cancel(account: LocalReaderAccountKey) = Unit
    }

    private companion object {
        fun account(profileId: String) =
            LocalReaderAccountKey.from("https://library.example", profileId)

        fun profile() = ConnectionProfile(
            serverOrigin = "https://library.example",
            serverBaseUrl = "https://library.example/",
            apiBaseUrl = "https://library.example/api/v1/",
            serverName = "Library",
            serverDescription = "",
            serverVersion = "1",
            serverReleaseDate = "2026-08-30",
            clientSessionId = "client-session",
            clientName = "Reader",
            clientType = "reader"
        )
    }
}
