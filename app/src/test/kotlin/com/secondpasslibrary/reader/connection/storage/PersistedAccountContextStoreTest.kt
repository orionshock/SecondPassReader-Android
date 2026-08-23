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

    private fun accountContext(sessionId: String, profileId: String) = PersistedAccountContext(
        AuthenticatedConnectionIdentity(
            apiBaseUrl = "https://library.example/api/v1/",
            clientSessionId = sessionId
        ),
        profileId
    )
}
