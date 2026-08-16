package com.secondpasslibrary.reader.app

import com.secondpasslibrary.client.SplClient
import org.junit.Assert.assertEquals
import org.junit.Test

class ApplicationFoundationMetadataTest {
    @Test
    fun `snapshot exposes the linked SDK identity`() {
        val snapshot = ApplicationFoundationMetadata().snapshot

        assertEquals(SplClient.identity.name, snapshot.clientName)
        assertEquals(SplClient.identity.version, snapshot.clientVersion)
        assertEquals("Android client foundation is ready.", snapshot.status)
    }
}
