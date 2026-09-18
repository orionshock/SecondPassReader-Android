package com.secondpasslibrary.reader.reader.annotations.mutation

import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.persistence.ReaderOutboxIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ReaderAnnotationDurableIntentTest {
    @Test
    fun `foreground and replay preserve equivalent SPL operations`() {
        val foreground = listOf(
            highlight(note = ""),
            highlight(clientId = "note", note = "A note"),
            ReaderAnnotationMutationRequest.UpsertBookmark(
                LOCAL_SESSION_ID,
                "bookmark",
                CFI,
                "Chapter 1"
            ),
            ReaderAnnotationMutationRequest.Delete(
                LOCAL_SESSION_ID,
                "delete",
                ReaderAnnotation.Bookmark("server", "delete", CFI, "Chapter 1", "now")
            )
        )
        val replay = foreground.mapIndexed { index, mutation ->
            ReaderOutboxIntent.Annotation(
                "annotation:$LOCAL_SESSION_ID:$index",
                "book",
                LOCAL_SESSION_ID,
                mutation
            ).mutation.forSession(SERVER_SESSION_ID)
        }

        assertEquals(
            foreground.map(ReaderAnnotationMutationRequest::toOperation),
            replay.map(ReaderAnnotationMutationRequest::toOperation)
        )
        assertEquals(listOf(SERVER_SESSION_ID), replay.map { it.sessionId }.distinct())
        assertNull((replay.last() as ReaderAnnotationMutationRequest.Delete).localSnapshot)
    }

    @Test
    fun `durable envelope rejects a mutation for another Session`() {
        assertThrows(IllegalArgumentException::class.java) {
            ReaderOutboxIntent.Annotation(
                "annotation:local:bookmark",
                "book",
                LOCAL_SESSION_ID,
                ReaderAnnotationMutationRequest.UpsertBookmark(
                    "different-session",
                    "bookmark",
                    CFI,
                    "Chapter 1"
                )
            )
        }
    }

    @Test
    fun `typed intent rejects invalid operation values before persistence`() {
        assertThrows(IllegalArgumentException::class.java) {
            highlight(clientId = "highlight", note = "").copy(text = "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReaderAnnotationMutationRequest.Delete(LOCAL_SESSION_ID, " ")
        }
    }

    private fun highlight(clientId: String = "highlight", note: String) =
        ReaderAnnotationMutationRequest.UpsertHighlight(
            LOCAL_SESSION_ID,
            clientId,
            CFI,
            "Chapter 1",
            "Quote",
            "Before",
            "After",
            ReaderAnnotationColor.YELLOW,
            note
        )

    private companion object {
        const val LOCAL_SESSION_ID = "local-session"
        const val SERVER_SESSION_ID = "server-session"
        const val CFI = "epubcfi(/6/2!/4/2:3)"
    }
}
