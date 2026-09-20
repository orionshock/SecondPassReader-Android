package com.secondpasslibrary.reader.reader.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPublicationInteractionPolicyTest {
    @Test
    fun `bounded edge zones leave the body available on narrow and wide viewports`() {
        val policy = ReaderPublicationInteractionPolicy
        assertEquals(ReaderInteractionZone.LEFT, policy.zone(55f, 1_000f, 1f))
        assertEquals(ReaderInteractionZone.BODY, policy.zone(56f, 1_000f, 1f))
        assertEquals(ReaderInteractionZone.BODY, policy.zone(943f, 1_000f, 1f))
        assertEquals(ReaderInteractionZone.RIGHT, policy.zone(944f, 1_000f, 1f))

        // On a narrow viewport, 18% caps the 56dp edge target.
        assertEquals(ReaderInteractionZone.LEFT, policy.zone(35f, 200f, 1f))
        assertEquals(ReaderInteractionZone.BODY, policy.zone(36f, 200f, 1f))
        assertEquals(ReaderInteractionZone.RIGHT, policy.zone(164f, 200f, 1f))
        assertEquals(ReaderInteractionZone.BODY, policy.zone(0f, 0f, 1f))
    }

    @Test
    fun `physical edges follow publication reading progression`() {
        val policy = ReaderPublicationInteractionPolicy
        assertEquals(
            ReaderPublicationAction.PREVIOUS_PAGE,
            policy.action(ReaderInteractionZone.LEFT, rightToLeft = false)
        )
        assertEquals(
            ReaderPublicationAction.NEXT_PAGE,
            policy.action(ReaderInteractionZone.RIGHT, rightToLeft = false)
        )
        assertEquals(
            ReaderPublicationAction.NEXT_PAGE,
            policy.action(ReaderInteractionZone.LEFT, rightToLeft = true)
        )
        assertEquals(
            ReaderPublicationAction.PREVIOUS_PAGE,
            policy.action(ReaderInteractionZone.RIGHT, rightToLeft = true)
        )
        assertEquals(
            ReaderPublicationAction.TOGGLE_CHROME,
            policy.action(ReaderInteractionZone.BODY, rightToLeft = true)
        )
    }

    @Test
    fun `edge swipe requires inward horizontal intent and a committed distance`() {
        val policy = ReaderPublicationInteractionPolicy
        assertTrue(policy.startsEdgeSwipe(ReaderInteractionZone.LEFT, 8f, 2f))
        assertTrue(policy.startsEdgeSwipe(ReaderInteractionZone.RIGHT, -8f, 2f))
        assertFalse(policy.startsEdgeSwipe(ReaderInteractionZone.LEFT, -8f, 2f))
        assertFalse(policy.startsEdgeSwipe(ReaderInteractionZone.RIGHT, 8f, 2f))
        assertFalse(policy.startsEdgeSwipe(ReaderInteractionZone.BODY, 8f, 2f))
        assertFalse(policy.startsEdgeSwipe(ReaderInteractionZone.LEFT, 2f, 8f))
        assertTrue(policy.completesEdgeSwipe(48f, 4f, density = 2f))
        assertFalse(policy.completesEdgeSwipe(47f, 4f, density = 2f))
        assertFalse(policy.completesEdgeSwipe(48f, 40f, density = 2f))
    }
}
