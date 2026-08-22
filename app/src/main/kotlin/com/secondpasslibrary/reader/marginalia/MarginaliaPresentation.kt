package com.secondpasslibrary.reader.marginalia

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal fun formatSessionTimestamp(value: String, zoneId: ZoneId, locale: Locale): String {
    val instant = runCatching { Instant.parse(value) }
        .recoverCatching { OffsetDateTime.parse(value).toInstant() }
        .getOrNull() ?: return value
    return DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(zoneId)
        .format(instant)
}

internal fun annotationCountLabel(count: Int) =
    if (count == 1) "1 annotation" else "$count annotations"
