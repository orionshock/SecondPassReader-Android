package com.secondpasslibrary.reader.app.storage

import com.secondpasslibrary.reader.home.projection.HomeAccountScopeKey
import com.secondpasslibrary.reader.reader.LocalReaderAccountKey
import com.secondpasslibrary.reader.reader.asset.ReaderAccountScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AccountLocalScopeTest {
    @Test
    fun `normalization and storage identity are defined by the common account scope`() {
        val canonical = AccountLocalScope.from(" HTTPS://LIBRARY.EXAMPLE/ ", " profile-1 ")
        val equivalent = AccountLocalScope.from("https://library.example", "profile-1")

        assertEquals(equivalent, canonical)
        assertEquals(canonical.storageKey, HomeAccountScopeKey.from(canonical).value)
        assertEquals(canonical.storageKey, LocalReaderAccountKey.from(canonical).value)
        assertEquals(canonical, ReaderAccountScope.from(canonical).account)
        assertNotEquals(
            canonical,
            AccountLocalScope.from("https://library.example", "profile-2")
        )
    }
}
