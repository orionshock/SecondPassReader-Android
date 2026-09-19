package com.secondpasslibrary.reader.connection

import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ApprovalExpiryPresenterTest {
    @Test
    fun formatsExpiryInDeviceLocalTime() {
        assertEquals(
            "Expires at 4:16 PM",
            ApprovalExpiryPresenter.label(
                "2026-09-19T23:16:00Z",
                ZoneId.of("America/Phoenix"),
                Locale.US
            ).replace('\u202f', ' ')
        )
    }

    @Test
    fun malformedExpiryNeverShowsMachineTimestamp() {
        assertEquals(
            "Approval code expires soon",
            ApprovalExpiryPresenter.label("not-a-date", ZoneId.of("UTC"), Locale.US)
        )
    }
}
