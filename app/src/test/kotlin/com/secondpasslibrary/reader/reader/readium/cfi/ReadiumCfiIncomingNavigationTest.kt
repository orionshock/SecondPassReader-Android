package com.secondpasslibrary.reader.reader.readium.cfi

import com.secondpasslibrary.reader.reader.cfi.EpubCfiFailure
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadiumCfiIncomingNavigationTest {
    @Test
    fun `missing DOM target is transient during resource arrival`() {
        assertTrue(EpubCfiFailure.DOM_TARGET_NOT_FOUND.isTransientResourceArrivalFailure())
    }

    @Test
    fun `invalid target and runtime failures are not retried as resource arrival`() {
        assertFalse(EpubCfiFailure.INVALID_RANGE.isTransientResourceArrivalFailure())
        assertFalse(EpubCfiFailure.CFI_RUNTIME_FAILURE.isTransientResourceArrivalFailure())
        assertFalse(EpubCfiFailure.JAVASCRIPT_RUNTIME_TIMEOUT.isTransientResourceArrivalFailure())
    }
}
