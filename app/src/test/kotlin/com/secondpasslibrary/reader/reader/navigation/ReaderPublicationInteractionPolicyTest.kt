package com.secondpasslibrary.reader.reader.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPublicationInteractionPolicyTest {
    @Test
    fun `tap zones use thirty forty thirty with deterministic boundaries`() {
        val policy = ReaderPublicationInteractionPolicy
        assertEquals(ReaderInteractionZone.LEFT, policy.tapZone(0f, 1_000f))
        assertEquals(ReaderInteractionZone.LEFT, policy.tapZone(299.999f, 1_000f))
        assertEquals(ReaderInteractionZone.BODY, policy.tapZone(300f, 1_000f))
        assertEquals(ReaderInteractionZone.BODY, policy.tapZone(300.001f, 1_000f))
        assertEquals(ReaderInteractionZone.BODY, policy.tapZone(700f, 1_000f))
        assertEquals(ReaderInteractionZone.RIGHT, policy.tapZone(700.001f, 1_000f))
        assertEquals(ReaderInteractionZone.RIGHT, policy.tapZone(1_000f, 1_000f))

        assertEquals(ReaderInteractionZone.LEFT, policy.tapZone(59f, 200f))
        assertEquals(ReaderInteractionZone.BODY, policy.tapZone(60f, 200f))
        assertEquals(ReaderInteractionZone.BODY, policy.tapZone(140f, 200f))
        assertEquals(ReaderInteractionZone.RIGHT, policy.tapZone(141f, 200f))
        assertEquals(ReaderInteractionZone.LEFT, policy.tapZone(767f, 2_560f))
        assertEquals(ReaderInteractionZone.BODY, policy.tapZone(768f, 2_560f))
        assertEquals(ReaderInteractionZone.BODY, policy.tapZone(1_792f, 2_560f))
        assertEquals(ReaderInteractionZone.RIGHT, policy.tapZone(1_793f, 2_560f))
        assertEquals(ReaderInteractionZone.BODY, policy.tapZone(0f, 0f))
    }

    @Test
    fun `swipe starts retain narrower bounded zones`() {
        val policy = ReaderPublicationInteractionPolicy
        assertEquals(ReaderInteractionZone.LEFT, policy.swipeZone(55f, 1_000f, 1f))
        assertEquals(ReaderInteractionZone.BODY, policy.swipeZone(56f, 1_000f, 1f))
        assertEquals(ReaderInteractionZone.BODY, policy.swipeZone(150f, 1_000f, 1f))
        assertEquals(ReaderInteractionZone.BODY, policy.swipeZone(943f, 1_000f, 1f))
        assertEquals(ReaderInteractionZone.RIGHT, policy.swipeZone(944f, 1_000f, 1f))
        assertEquals(ReaderInteractionZone.LEFT, policy.swipeZone(35f, 200f, 1f))
        assertEquals(ReaderInteractionZone.BODY, policy.swipeZone(36f, 200f, 1f))
        assertEquals(ReaderInteractionZone.RIGHT, policy.swipeZone(164f, 200f, 1f))
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
