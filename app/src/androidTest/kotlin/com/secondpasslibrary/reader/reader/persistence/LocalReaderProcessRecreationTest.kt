package com.secondpasslibrary.reader.reader.persistence

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.secondpasslibrary.reader.reader.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationRequest
import com.secondpasslibrary.reader.reader.cfi.EpubCfi
import com.secondpasslibrary.reader.reader.progress.ReaderProgressPersistenceController
import com.secondpasslibrary.reader.reader.progress.ReaderProgressState
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import com.secondpasslibrary.reader.storage.database.SecondPassLocalDatabase
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalReaderProcessRecreationTest {
    @Test
    fun offlineReaderProjectionSurvivesDatabaseAndOwnerRecreation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "reader-process-${UUID.randomUUID()}.db"
        val account = LocalReaderAccountKey.from("https://library.example", "profile-1")
        val firstDatabase = open(context, databaseName)
        val firstStore = RoomLocalReaderStateStore(firstDatabase.localReaderDao())
        val session = firstStore.selectOfflineSession(account, "book-1")
        firstStore.writeProgress(
            account,
            session.sessionId,
            CFI_A,
            LocalReaderWriteProvenance.LOCAL_PENDING
        )
        val progressScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val progress = MutableStateFlow<ReaderProgressState?>(
            ReaderProgressState(
                session.sessionId,
                ReaderSessionStatus.ACTIVE,
                captureEnabled = true,
                EpubCfi(CFI_B),
                candidateVersion = 1
            )
        )
        val progressPersistence = ReaderProgressPersistenceController(
            progressScope,
            firstStore
        ) {}
        progressPersistence.start(account, session, progress)
        progressPersistence.flushLatestLocal()
        progressPersistence.close()
        progressScope.cancel()
        firstStore.applyAnnotationMutation(
            account,
            session.sessionId,
            ReaderAnnotationMutationRequest.UpsertHighlight(
                session.sessionId,
                "client-1",
                RANGE_CFI,
                "Chapter 1",
                "quote",
                "prefix",
                "suffix",
                ReaderAnnotationColor.YELLOW,
                "offline note"
            )
        )
        firstDatabase.close()

        val reopenedDatabase = open(context, databaseName)
        try {
            val reopenedStore = RoomLocalReaderStateStore(reopenedDatabase.localReaderDao())
            val reopenedOutbox = RoomReaderOutboxStore(reopenedDatabase.localReaderDao())
            val reopenedSession = reopenedStore.selectOfflineSession(account, "book-1")
            val annotation = reopenedStore.readAnnotations(
                account,
                reopenedSession.sessionId
            ).single()
                as ReaderAnnotation.Highlight

            assertEquals(session.sessionId, reopenedSession.sessionId)
            assertEquals(CFI_B, reopenedSession.savedProgressCfi)
            assertEquals("offline note", annotation.note)
            val intents = reopenedOutbox.pendingReaderIntents(account, reopenedSession.sessionId)
            assertEquals(3, intents.size)
            assertEquals(1, intents.count { it is ReaderOutboxIntent.EstablishSession })
            assertEquals(
                CFI_B,
                (
                    intents.single { it is ReaderOutboxIntent.Progress } as
                        ReaderOutboxIntent.Progress
                    ).cfi
            )
            assertEquals(
                "offline note",
                (
                    intents.single {
                        it is ReaderOutboxIntent.AnnotationUpsert
                    } as ReaderOutboxIntent.AnnotationUpsert
                    ).note
            )
        } finally {
            reopenedDatabase.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun open(context: Context, name: String) = Room.databaseBuilder(
        context,
        SecondPassLocalDatabase::class.java,
        name
    ).build()

    private companion object {
        const val CFI_A = "epubcfi(/6/2!/4/2:3)"
        const val CFI_B = "epubcfi(/6/4!/4/2:7)"
        const val RANGE_CFI = "epubcfi(/6/2!/4/2,:3,:9)"
    }
}
