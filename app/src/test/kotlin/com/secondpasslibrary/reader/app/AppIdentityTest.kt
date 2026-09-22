package com.secondpasslibrary.reader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AppIdentityTest {
    @Test
    fun `pairing client type identifies installed app family and version`() {
        assertEquals("SPR-Android-0.1.0-alpha.1", pairingClientType("0.1.0-alpha.1"))
    }
}
