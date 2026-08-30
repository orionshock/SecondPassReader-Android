package com.secondpasslibrary.reader.reader.persistence

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.secondpasslibrary.reader.home.projection.SecondPassReaderDatabase
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationMutationRequest
import java.util.UUID
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
            CFI,
            LocalReaderWriteProvenance.LOCAL_PENDING
        )
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
            assertEquals(CFI, reopenedSession.savedProgressCfi)
            assertEquals("offline note", annotation.note)
            val intents = reopenedOutbox.pendingReaderIntents(account, reopenedSession.sessionId)
            assertEquals(3, intents.size)
            assertEquals(1, intents.count { it is ReaderOutboxIntent.EstablishSession })
            assertEquals(
                CFI,
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
        SecondPassReaderDatabase::class.java,
        name
    ).build()

    private companion object {
        const val CFI = "epubcfi(/6/2!/4/2:3)"
        const val RANGE_CFI = "epubcfi(/6/2!/4/2,:3,:9)"
    }
}
