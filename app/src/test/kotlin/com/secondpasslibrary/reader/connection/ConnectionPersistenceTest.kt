package com.secondpasslibrary.reader.connection

import com.secondpasslibrary.client.BearerCredential
import com.secondpasslibrary.reader.connection.storage.PersistedAccountContextStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

internal class ConnectionPersistenceTest : ConnectionCoordinatorTestSupport() {
    @Test
    fun `commit journals credential before profile and then marks it committed`() = runTest {
        val fixture = fixture()

        val result = fixture.persistence.commit(profile(), fixture.credential)

        assertEquals(DurableConnectionCommit.Committed, result)
        assertEquals(
            listOf("credential-write", "profile-write", "credential-commit"),
            fixture.events
        )
        assertEquals(profile(), fixture.profileStore.stored)
        assertNull(fixture.credentialStore.stored?.recoveryProfile)
    }

    @Test
    fun `failed profile write leaves a recoverable credential journal`() = runTest {
        val fixture = fixture()
        fixture.profileStore.failWrite = true

        val result = fixture.persistence.commit(profile(), fixture.credential)

        assertEquals(DurableConnectionCommit.RecoveryRequired(profile()), result)
        assertEquals(listOf("credential-write", "profile-write"), fixture.events)
        assertNull(fixture.profileStore.stored)
        assertEquals(profile(), fixture.credentialStore.stored?.recoveryProfile)
    }

    @Test
    fun `failed secure credential write does not attempt profile commit`() = runTest {
        val fixture = fixture()
        fixture.credentialStore.failWrite = true

        val result = fixture.persistence.commit(profile(), fixture.credential)

        assertEquals(DurableConnectionCommit.SecureCredentialFailed, result)
        assertEquals(listOf("credential-write"), fixture.events)
        assertNull(fixture.profileStore.stored)
    }

    @Test
    fun `commit marker compaction failure leaves a recoverable committed connection`() = runTest {
        val fixture = fixture()
        fixture.credentialStore.failCommitMarker = true

        val result = fixture.persistence.commit(profile(), fixture.credential)

        assertEquals(DurableConnectionCommit.Committed, result)
        assertEquals(profile(), fixture.profileStore.stored)
        assertEquals(profile(), fixture.credentialStore.stored?.recoveryProfile)
        val restored = fixture.persistence.restore { _, _ -> }
        assertTrue(restored is DurableConnectionRestore.Committed)
    }

    @Test
    fun `restore repairs an interrupted commit from the secure journal`() = runTest {
        val fixture = fixture()
        fixture.credentialStore.stored = StoredCredential(fixture.credential, profile())
        var availableProfile: ConnectionProfile? = null

        val result = fixture.persistence.restore { restoredProfile, _ ->
            availableProfile = restoredProfile
        }

        assertTrue(result is DurableConnectionRestore.Committed)
        assertEquals(profile(), availableProfile)
        assertEquals(profile(), fixture.profileStore.stored)
        assertNull(fixture.credentialStore.stored?.recoveryProfile)
        assertEquals(listOf("profile-write", "credential-commit"), fixture.events)
    }

    @Test
    fun `restore reports recovery when journal repair still cannot write profile`() = runTest {
        val fixture = fixture()
        fixture.credentialStore.stored = StoredCredential(fixture.credential, profile())
        fixture.profileStore.failWrite = true

        val result = fixture.persistence.restore { _, _ -> }

        assertEquals(DurableConnectionRestore.RecoveryRequired(profile()), result)
        assertEquals(profile(), fixture.credentialStore.stored?.recoveryProfile)
    }

    @Test
    fun `profile without credential is incomplete and is cleared`() = runTest {
        val fixture = fixture()
        fixture.profileStore.stored = profile()

        val result = fixture.persistence.restore { _, _ -> }

        assertEquals(DurableConnectionRestore.None, result)
        assertNull(fixture.profileStore.stored)
        assertEquals(
            listOf("profile-clear", "credential-clear", "account-clear"),
            fixture.events
        )
    }

    @Test
    fun `credential without profile or recovery journal is incomplete and is cleared`() = runTest {
        val fixture = fixture()
        fixture.credentialStore.stored = StoredCredential(fixture.credential, null)

        val result = fixture.persistence.restore { _, _ -> }

        assertEquals(DurableConnectionRestore.None, result)
        assertNull(fixture.credentialStore.stored)
        assertEquals(
            listOf("profile-clear", "credential-clear", "account-clear"),
            fixture.events
        )
    }

    @Test
    fun `corrupt secure credential state is reported without clearing profile evidence`() =
        runTest {
            val fixture = fixture()
            fixture.profileStore.stored = profile()
            fixture.credentialStore.failRead = true

            val failure = runCatching {
                fixture.persistence.restore { _, _ -> }
            }.exceptionOrNull()

            assertSame(fixture.credentialStore.readFailure, failure)
            assertEquals(profile(), fixture.profileStore.stored)
            assertTrue(fixture.events.isEmpty())
        }

    @Test
    fun `clear attempts every store and reports credential failure after profile removal`() =
        runTest {
            val fixture = fixture()
            fixture.profileStore.stored = profile()
            fixture.credentialStore.stored = StoredCredential(fixture.credential, null)
            fixture.accountStore.stored = persistedAccount()
            fixture.credentialStore.failClear = true

            val failure = runCatching { fixture.persistence.clear() }.exceptionOrNull()

            assertTrue(failure is ConnectionPersistenceClearException)
            assertSame(fixture.credentialStore.clearFailure, failure?.cause)
            assertEquals(
                listOf("profile-clear", "credential-clear", "account-clear"),
                fixture.events
            )
            assertNull(fixture.profileStore.stored)
            assertNull(fixture.accountStore.stored)
            assertFalse(fixture.credentialStore.stored == null)
        }

    @Test
    fun `repeated clear converges after a partial clear failure`() = runTest {
        val fixture = fixture()
        fixture.profileStore.stored = profile()
        fixture.credentialStore.stored = StoredCredential(fixture.credential, null)
        fixture.accountStore.stored = persistedAccount()
        fixture.credentialStore.failClear = true
        runCatching { fixture.persistence.clear() }
        fixture.credentialStore.failClear = false
        fixture.events.clear()

        fixture.persistence.clear()

        assertEquals(
            listOf("profile-clear", "credential-clear", "account-clear"),
            fixture.events
        )
        assertNull(fixture.profileStore.stored)
        assertNull(fixture.credentialStore.stored)
        assertNull(fixture.accountStore.stored)
    }

    private fun fixture(): Fixture {
        val events = mutableListOf<String>()
        val profileStore = TrackingProfileStore(events)
        val credentialStore = TrackingCredentialStore(events)
        val accountStore = TrackingAccountStore(events)
        return Fixture(
            events,
            profileStore,
            credentialStore,
            accountStore,
            ConnectionPersistence(profileStore, credentialStore, accountStore, FakeRoutesStore()),
            BearerCredential.restore("spl_secret")
        )
    }

    private fun persistedAccount() = PersistedAccountContext(profile(), "profile-1")

    private data class Fixture(
        val events: MutableList<String>,
        val profileStore: TrackingProfileStore,
        val credentialStore: TrackingCredentialStore,
        val accountStore: TrackingAccountStore,
        val persistence: ConnectionPersistence,
        val credential: BearerCredential
    )

    private class TrackingProfileStore(private val events: MutableList<String>) :
        ConnectionProfileStore {
        var stored: ConnectionProfile? = null
        var failWrite = false

        override suspend fun read(): ConnectionProfile? = stored

        override suspend fun write(profile: ConnectionProfile) {
            events += "profile-write"
            if (failWrite) throw ConnectionProfileStorageException("profile write failed")
            stored = profile
        }

        override suspend fun clear() {
            events += "profile-clear"
            stored = null
        }
    }

    private class TrackingCredentialStore(private val events: MutableList<String>) :
        BearerCredentialStore {
        var stored: StoredCredential? = null
        var failWrite = false
        var failCommitMarker = false
        var failClear = false
        var failRead = false
        val readFailure = CredentialStorageException("credential read failed")
        val clearFailure = CredentialStorageException("credential clear failed")

        override suspend fun read(): StoredCredential? {
            if (failRead) throw readFailure
            return stored
        }

        override suspend fun write(
            credential: BearerCredential,
            recoveryProfile: ConnectionProfile
        ) {
            events += "credential-write"
            if (failWrite) throw CredentialStorageException("credential write failed")
            stored = StoredCredential(credential, recoveryProfile)
        }

        override suspend fun markProfileCommitted() {
            events += "credential-commit"
            if (failCommitMarker) throw CredentialStorageException("marker write failed")
            stored = stored?.copy(recoveryProfile = null)
        }

        override suspend fun clear() {
            events += "credential-clear"
            if (failClear) throw clearFailure
            stored = null
        }
    }

    private class TrackingAccountStore(private val events: MutableList<String>) :
        PersistedAccountContextStore {
        var stored: PersistedAccountContext? = null

        override suspend fun read(): PersistedAccountContext? = stored

        override suspend fun write(context: PersistedAccountContext) {
            stored = context
        }

        override suspend fun clear() {
            events += "account-clear"
            stored = null
        }
    }
}
