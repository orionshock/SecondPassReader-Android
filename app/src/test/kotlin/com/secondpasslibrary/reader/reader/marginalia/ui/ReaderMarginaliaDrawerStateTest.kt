package com.secondpasslibrary.reader.reader.marginalia.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderMarginaliaDrawerStateTest {
    @Test
    fun `current is default and selection does not carry visibility behavior`() {
        val state = ReaderMarginaliaDrawerState("current")

        state.select("previous", setOf("current", "previous"))

        assertEquals("previous", state.selectedLayerSessionId)
        assertTrue(state.showingLayerContent)
    }

    @Test
    fun `missing selection and Reader replacement fall back to current`() {
        val state = ReaderMarginaliaDrawerState("current-a")
        state.select("previous", setOf("current-a", "previous"))

        state.reconcile("current-a", emptySet())
        assertEquals("current-a", state.selectedLayerSessionId)
        assertFalse(state.showingLayerContent)

        state.select("previous-b", setOf("current-a", "previous-b"))
        state.reconcile("current-b", setOf("previous-b"))
        assertEquals("current-b", state.selectedLayerSessionId)
        assertFalse(state.showingLayerContent)
    }
}
