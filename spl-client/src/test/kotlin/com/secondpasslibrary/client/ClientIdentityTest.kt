package com.secondpasslibrary.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ClientIdentityTest {
    @Test
    fun `preserves a valid identity`() {
        val identity = ClientIdentity(name = "Client", version = "1.2.3")

        assertEquals("Client", identity.name)
        assertEquals("1.2.3", identity.version)
    }

    @Test
    fun `rejects a blank name`() {
        assertThrows(IllegalArgumentException::class.java) {
            ClientIdentity(name = " ", version = "1.2.3")
        }
    }

    @Test
    fun `rejects a blank version`() {
        assertThrows(IllegalArgumentException::class.java) {
            ClientIdentity(name = "Client", version = " ")
        }
    }
}
