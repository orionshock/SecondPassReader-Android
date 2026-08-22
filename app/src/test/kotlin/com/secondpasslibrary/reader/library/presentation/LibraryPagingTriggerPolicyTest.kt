package com.secondpasslibrary.reader.library.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryPagingTriggerPolicyTest {
    @Test
    fun `paging trigger starts only near the loaded result boundary`() {
        assertFalse(
            LibraryPagingTriggerPolicy.shouldRequestNextPage(lastVisibleIndex = 10, itemCount = 50)
        )
        assertTrue(
            LibraryPagingTriggerPolicy.shouldRequestNextPage(lastVisibleIndex = 44, itemCount = 50)
        )
        assertFalse(
            LibraryPagingTriggerPolicy.shouldRequestNextPage(lastVisibleIndex = -1, itemCount = 0)
        )
    }
}
