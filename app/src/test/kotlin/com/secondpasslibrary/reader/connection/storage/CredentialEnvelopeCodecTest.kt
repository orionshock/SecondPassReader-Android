package com.secondpasslibrary.reader.connection.storage

import com.secondpasslibrary.client.BearerCredential
import com.secondpasslibrary.reader.connection.ConnectionProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialEnvelopeCodecTest {
    @Test
    fun `round trips credential with a transaction recovery profile`() {
        val encoded = CredentialEnvelopeCodec.encode(
            BearerCredential.restore("spl_secret"),
            profile()
        )

        val decoded = CredentialEnvelopeCodec.decode(encoded)

        decoded.credential.useSecret { assertEquals("spl_secret", it) }
        assertEquals(profile(), decoded.recoveryProfile)
        assertFalse(encoded.decodeToString().contains("spl_secret"))
    }

    @Test
    fun `committed envelope has no recovery profile`() {
        val decoded = CredentialEnvelopeCodec.decode(
            CredentialEnvelopeCodec.encode(BearerCredential.restore("spl_secret"), null)
        )

        assertNull(decoded.recoveryProfile)
        assertTrue(decoded.credential.toString().contains("redacted"))
    }

    private fun profile() = ConnectionProfile(
        "https://library.example",
        "https://library.example/",
        "https://library.example/api/v1/",
        "Library",
        "Books",
        "1.0",
        "2026-08-16",
        "session-1",
        "Tablet",
        "second-pass-android-client"
    )
}
