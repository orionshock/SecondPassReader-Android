package com.secondpasslibrary.reader.reader.ui.hud

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.reader.appearance.ReaderPalette
import com.secondpasslibrary.reader.reader.domain.ReaderReadingStatus
import com.secondpasslibrary.reader.reader.domain.ReaderReadingStatusScope
import java.time.Clock
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@Composable
internal fun ReaderAmbientHud(
    visible: Boolean,
    readingStatus: ReaderReadingStatus?,
    palette: ReaderPalette
) {
    val context = LocalContext.current
    val use24Hour = DateFormat.is24HourFormat(context)
    val time by produceState(
        initialValue = formatReaderClock(LocalTime.now(), use24Hour),
        key1 = use24Hour
    ) {
        val clock = Clock.systemDefaultZone()
        while (true) {
            val now = LocalTime.now(clock)
            value = formatReaderClock(now, use24Hour)
            delay(millisUntilNextMinute(now))
        }
    }
    Box(
        Modifier.fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .alpha(if (visible) HUD_VISIBLE_ALPHA else HUD_AMBIENT_ALPHA)
    ) {
        Row(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = time,
                modifier = Modifier.testTag(READER_HUD_CLOCK_TAG),
                color = palette.secondaryForeground,
                style = MaterialTheme.typography.labelSmall
            )
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            readerReadingStatusLabel(readingStatus)?.let { label ->
                Text(
                    text = label,
                    modifier = Modifier.testTag(READER_HUD_STATUS_TAG),
                    color = palette.secondaryForeground,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

internal fun formatReaderClock(time: LocalTime, use24Hour: Boolean): String =
    time.format(if (use24Hour) CLOCK_24_HOUR else CLOCK_12_HOUR)

internal fun millisUntilNextMinute(time: LocalTime): Long {
    val nextMinute = time.withSecond(0).withNano(0).plusMinutes(1)
    return Duration.between(time, nextMinute).toMillis().coerceAtLeast(1L)
}

internal fun readerReadingStatusLabel(status: ReaderReadingStatus?): String? {
    if (status == null || status.pagesRemaining <= 0) return null
    val pages = if (status.pagesRemaining == 1) "1 page" else "${status.pagesRemaining} pages"
    return when (status.scope) {
        ReaderReadingStatusScope.SECTION -> "$pages left in section"
    }
}

internal const val READER_HUD_CLOCK_TAG = "reader_hud_clock"
internal const val READER_HUD_STATUS_TAG = "reader_hud_status"

private val CLOCK_12_HOUR = DateTimeFormatter.ofPattern("h:mm a")
private val CLOCK_24_HOUR = DateTimeFormatter.ofPattern("HH:mm")
private const val HUD_VISIBLE_ALPHA = 0.76f
private const val HUD_AMBIENT_ALPHA = 0.38f
