package com.secondpasslibrary.reader.connection

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal object ApprovalExpiryPresenter {
    fun label(
        expiresAt: String,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault()
    ): String {
        val localTime = runCatching {
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                .withLocale(locale)
                .withZone(zone)
                .format(Instant.parse(expiresAt))
        }.getOrNull()
        return localTime?.let { "Expires at $it" } ?: "Approval code expires soon"
    }
}
