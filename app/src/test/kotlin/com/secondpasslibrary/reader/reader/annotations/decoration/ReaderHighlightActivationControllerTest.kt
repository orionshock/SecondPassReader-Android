package com.secondpasslibrary.reader.reader.annotations.decoration

import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotation
import com.secondpasslibrary.reader.reader.annotations.ReaderAnnotationColor
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayerLoadState
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayerRole
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayerSummary
import com.secondpasslibrary.reader.reader.marginalia.ReaderPreviousMarginaliaLayer
import com.secondpasslibrary.reader.reader.session.ReaderSessionContext
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderHighlightActivationControllerTest {
    @Test
    fun `active current activation opens existing edit path`() = runTest {
        val edits = mutableListOf<ReaderAnnotation.Highlight>()
        val target = ActivationDecorations()
        val controller = ReaderHighlightActivationController(backgroundScope, edits::add)
        val annotation = highlight("annotation", "current-client")
        controller.replaceContext(
            session("current", ReaderSessionStatus.ACTIVE),
            listOf(annotation),
            emptyList()
        )
        controller.select(target)
        runCurrent()

        target.activate(currentActivation("current", annotation.id))
        runCurrent()

        assertEquals(listOf(annotation), edits)
        assertNull(controller.detail.value)
    }

    @Test
    fun `closed current activation opens read-only detail`() = runTest {
        val target = ActivationDecorations()
        val controller = ReaderHighlightActivationController(backgroundScope) {
            error("Must stay read-only")
        }
        val annotation = highlight("annotation", "client")
        controller.replaceContext(
            session("current", ReaderSessionStatus.CLOSED),
            listOf(annotation),
            emptyList()
        )
        controller.select(target)
        runCurrent()

        target.activate(currentActivation("current", annotation.id))
        runCurrent()

        assertEquals(annotation, controller.detail.value?.annotation)
        assertEquals(false, controller.detail.value?.historical)
    }

    @Test
    fun `previous activation keeps Session identity when client IDs overlap`() = runTest {
        val target = ActivationDecorations()
        val controller = ReaderHighlightActivationController(backgroundScope) {
            error("Must stay read-only")
        }
        val current = highlight("current-id", "shared-client")
        val previous = highlight("previous-id", "shared-client")
        controller.replaceContext(
            session("current", ReaderSessionStatus.ACTIVE),
            listOf(current),
            listOf(previousLayer("previous", previous))
        )
        controller.select(target)
        runCurrent()

        target.activate(
            ReaderAnnotationDecorationActivation(
                sessionId = "previous",
                groupId = ReaderAnnotationDecorationGroupId.Previous("previous"),
                annotationId = previous.id
            )
        )
        runCurrent()

        assertEquals("previous", controller.detail.value?.sessionId)
        assertEquals(previous, controller.detail.value?.annotation)
        assertEquals(true, controller.detail.value?.historical)
    }

    @Test
    fun `removed or mismatched activation is ignored`() = runTest {
        val target = ActivationDecorations()
        val controller = ReaderHighlightActivationController(backgroundScope) {
            error("Must be ignored")
        }
        controller.replaceContext(
            session("current", ReaderSessionStatus.ACTIVE),
            emptyList(),
            emptyList()
        )
        controller.select(target)
        runCurrent()

        target.activate(currentActivation("current", "removed"))
        target.activate(
            ReaderAnnotationDecorationActivation(
                sessionId = "other",
                groupId = ReaderAnnotationDecorationGroupId.Previous("different"),
                annotationId = "removed"
            )
        )
        runCurrent()

        assertNull(controller.detail.value)
    }
}

private class ActivationDecorations : ReaderAnnotationDecorations {
    private val mutableActivations = MutableSharedFlow<ReaderAnnotationDecorationActivation>(
        extraBufferCapacity = 8
    )
    override val activations = mutableActivations
    override val failures = MutableStateFlow(emptyMap<String, ReaderAnnotationDecorationFailure>())

    fun activate(value: ReaderAnnotationDecorationActivation) {
        check(mutableActivations.tryEmit(value))
    }

    override suspend fun replace(
        groupId: ReaderAnnotationDecorationGroupId,
        decorations: List<ReaderAnnotationDecoration>
    ) = Unit

    override suspend fun clear(groupId: ReaderAnnotationDecorationGroupId) = Unit
}

private fun currentActivation(sessionId: String, annotationId: String) =
    ReaderAnnotationDecorationActivation(
        sessionId = sessionId,
        groupId = ReaderAnnotationDecorationGroupId.Current,
        annotationId = annotationId
    )

private fun session(id: String, status: ReaderSessionStatus) = ReaderSessionContext(
    sessionId = id,
    status = status,
    savedProgressCfi = null,
    sessionName = "Session $id",
    startedAt = "2026-08-20T00:00:00Z"
)

private fun previousLayer(sessionId: String, annotation: ReaderAnnotation) =
    ReaderPreviousMarginaliaLayer(
        summary = ReaderMarginaliaLayerSummary(
            sessionId = sessionId,
            role = ReaderMarginaliaLayerRole.PREVIOUS,
            sessionStatus = ReaderSessionStatus.CLOSED,
            sessionName = "Previous session",
            startedAt = "2026-08-01T00:00:00Z",
            closedAt = "2026-08-02T00:00:00Z",
            lastActivityAt = "2026-08-02T00:00:00Z",
            annotationCount = 1
        ),
        loadState = ReaderMarginaliaLayerLoadState.LOADED,
        annotations = listOf(annotation)
    )

private fun highlight(id: String, clientId: String) = ReaderAnnotation.Highlight(
    id = id,
    clientId = clientId,
    cfi = "epubcfi(/6/4!/4/2,/1:0,/1:4)",
    locationLabel = "Chapter 01 · 10%",
    updatedAt = "2026-08-20T00:00:00Z",
    quote = "Quote",
    prefix = "Before",
    suffix = "After",
    note = "Note",
    color = ReaderAnnotationColor.YELLOW
)
