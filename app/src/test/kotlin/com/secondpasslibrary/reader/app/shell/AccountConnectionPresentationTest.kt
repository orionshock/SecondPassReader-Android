package com.secondpasslibrary.reader.app.shell

import com.secondpasslibrary.reader.app.AppSessionAuthority
import com.secondpasslibrary.reader.connection.ConnectionUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountConnectionPresentationTest {
    @Test
    fun `background verification stays ambient`() {
        val authority =
            AppSessionAuthority.Healing(ConnectionUiState.VerifyingServer("https://spl.test"))

        assertFalse(authority.requiresInteractiveConnectionPresentation())
    }

    @Test
    fun `interactive relink remains visible`() {
        val authority =
            AppSessionAuthority.Healing(ConnectionUiState.TerminalPairingProblem("Try again"))

        assertTrue(authority.requiresInteractiveConnectionPresentation())
    }
}
