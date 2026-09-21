package com.secondpasslibrary.reader.connection.storage

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.secondpasslibrary.reader.connection.AuthenticatedConnectionIdentity
import com.secondpasslibrary.reader.connection.PersistedAccountContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersistedAccountContextStoreTest {
    @Test
    fun `descriptor round trips through preferences`() {
        val preferences = mutablePreferencesOf()
        val context = accountContext("session-1", "profile-1")

        preferences.putAccountContext(context)

        assertEquals(context, preferences.toAccountContext())
    }

    @Test
    fun `writing replacement replaces every descriptor field`() {
        val preferences = mutablePreferencesOf()
        preferences.putAccountContext(accountContext("session-1", "profile-1"))
        val replacement = accountContext("session-2", "profile-2")

        preferences.putAccountContext(replacement)

        assertEquals(replacement, preferences.toAccountContext())
    }

    @Test
    fun `clearing descriptor removes it`() {
        val preferences = mutablePreferencesOf()
        preferences.putAccountContext(accountContext("session-1", "profile-1"))

        preferences.clearAccountContext()

        assertNull(preferences.toAccountContext())
    }

    @Test
    fun `absent descriptor is normal`() {
        val preferences = mutablePreferencesOf()

        assertNull(preferences.toAccountContext())
    }

    @Test
    fun `old URL-derived descriptor is not read or migrated`() {
        val preferences = mutablePreferencesOf()
        preferences[androidx.datastore.preferences.core.stringPreferencesKey("library_base_url")] =
            "https://library.example"
        preferences[androidx.datastore.preferences.core.stringPreferencesKey("client_session_id")] =
            "session-1"
        preferences[androidx.datastore.preferences.core.stringPreferencesKey("profile_id")] =
            "profile-1"

        assertNull(preferences.toAccountContext())
    }

    private fun accountContext(sessionId: String, profileId: String) = PersistedAccountContext(
        AuthenticatedConnectionIdentity(
            serverId = "a6722b5a-7982-4778-8c74-39be4241a654",
            profileId = profileId
        ),
        sessionId
    )
}
