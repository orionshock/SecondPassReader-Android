package com.secondpasslibrary.reader.reader.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderClosedSessionContinuationPolicyTest {
    @Test
    fun `pending progress alone requires continuation and moves forward`() {
        val plan = plan(pendingProgress = 10)
        assertTrue(plan.needsContinuation)
        assertEquals(ReaderContinuationProgressSource.CLOSED_SESSION, plan.progressSource)
        assertEquals(0, plan.forwardedEditCount)
        assertEquals(0, plan.droppedDeleteCount)
    }

    @Test
    fun `progress chooses newest timestamp and source wins ties`() {
        assertEquals(
            ReaderContinuationProgressSource.CONTINUATION,
            plan(pendingProgress = 10, targetProgress = 11).progressSource
        )
        assertEquals(
            ReaderContinuationProgressSource.CLOSED_SESSION,
            plan(pendingProgress = 10, targetProgress = 10).progressSource
        )
        assertEquals(
            ReaderContinuationProgressSource.CLOSED_SESSION,
            plan(pendingProgress = 11, targetProgress = 10).progressSource
        )
        assertNull(plan(targetProgress = 11).progressSource)
        assertFalse(plan(targetProgress = 11).needsContinuation)
    }

    @Test
    fun `new authored annotation retains identity without counting a confirmed edit`() {
        val plan = plan(listOf(ReaderContinuationAnnotation("new", false, false)))
        assertTrue(plan.needsContinuation)
        assertEquals(
            listOf(ReaderContinuationAnnotationCopy("new", "new", false)),
            plan.annotationCopies
        )
        assertEquals(0, plan.forwardedEditCount)
    }

    @Test
    fun `confirmed edit gets deterministic replacement rather than historical identity`() {
        val pending = listOf(ReaderContinuationAnnotation("edited", true, false))
        val first = plan(pending)
        assertEquals(
            listOf(
                ReaderContinuationAnnotationCopy(
                    "edited",
                    "69f00aba-31c0-307c-adf4-f91e1fc9ca25",
                    true
                )
            ),
            first.annotationCopies
        )
        assertEquals(1, first.forwardedEditCount)
        assertEquals(first, plan(pending))
        val anotherTarget = ReaderClosedSessionContinuationPolicy.plan(
            "source",
            "another-target",
            pending,
            null,
            null
        )
        assertNotEquals(first.annotationCopies, anotherTarget.annotationCopies)
    }

    @Test
    fun `confirmed deletes are dropped and abandoned new annotations need no continuation`() {
        val plan = plan(
            listOf(
                ReaderContinuationAnnotation("historical-delete", true, true),
                ReaderContinuationAnnotation("never-confirmed-delete", false, true)
            )
        )
        assertFalse(plan.needsContinuation)
        assertTrue(plan.annotationCopies.isEmpty())
        assertEquals(1, plan.droppedDeleteCount)
        assertEquals(0, plan.forwardedEditCount)
    }

    @Test
    fun `mixed intent counts only forwarded confirmed edits and dropped confirmed deletes`() {
        val pending = listOf(
            ReaderContinuationAnnotation("edited", true, false),
            ReaderContinuationAnnotation("new", false, false),
            ReaderContinuationAnnotation("deleted", true, true),
            ReaderContinuationAnnotation("abandoned", false, true)
        )
        val first = plan(pending, pendingProgress = 10)
        assertEquals(2, first.annotationCopies.size)
        assertEquals(1, first.forwardedEditCount)
        assertEquals(1, first.droppedDeleteCount)
        assertEquals(first, plan(pending, pendingProgress = 10))
        assertFalse(plan().needsContinuation)
    }

    private fun plan(
        annotations: List<ReaderContinuationAnnotation> = emptyList(),
        pendingProgress: Long? = null,
        targetProgress: Long? = null
    ) = ReaderClosedSessionContinuationPolicy.plan(
        "source",
        "target",
        annotations,
        pendingProgress,
        targetProgress
    )
}
