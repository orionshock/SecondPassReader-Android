package com.secondpasslibrary.reader.reader.persistence

import com.secondpasslibrary.reader.reader.session.ReaderSessionIdentityKind
import com.secondpasslibrary.reader.reader.session.ReaderSessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalReaderPersistedStateValidatorTest {
    @Test
    fun validSessionStatesMapAtThePersistenceBoundary() {
        assertEquals(
            ReaderSessionIdentityKind.PROVISIONAL,
            provisional().toContext(null).identityKind
        )
        assertEquals(
            ReaderSessionStatus.CLOSED,
            confirmed().copy(serverStatus = ReaderSessionStatus.CLOSED.name)
                .toContext(null).status
        )
    }

    @Test
    fun malformedSessionStateFailsAtOnePredictableBoundary() {
        assertThrows(InvalidLocalReaderStateException::class.java) {
            provisional().copy(serverSessionId = "server-id").toContext(null)
        }
        assertThrows(InvalidLocalReaderStateException::class.java) {
            confirmed().copy(identityKind = "UNKNOWN").toContext(null)
        }
    }

    @Test
    fun malformedOutboxPayloadFailsBeforeDeliveryMapping() {
        val malformed = LocalReaderOutboxEntity(
            "account", "progress:local", "book", "local",
            ReaderOutboxOperation.PROGRESS, null, READER_MUTATION_DELIVERY_ORDER,
            null, null, null, null, null, null, null, null, 1
        )

        assertThrows(InvalidLocalReaderStateException::class.java) { malformed.toIntent() }
    }

    private fun provisional() = LocalReaderSessionEntity(
        "account", "local", "book", null, ReaderSessionIdentityKind.PROVISIONAL.name,
        null, "book", null, "", null, null, null, null, 1, 1
    )

    private fun confirmed() = LocalReaderSessionEntity(
        "account", "local", "book", "server-id",
        ReaderSessionIdentityKind.SERVER_CONFIRMED.name, ReaderSessionStatus.ACTIVE.name,
        null, null, "", null, null, null, 2, 1, 1
    )
}
