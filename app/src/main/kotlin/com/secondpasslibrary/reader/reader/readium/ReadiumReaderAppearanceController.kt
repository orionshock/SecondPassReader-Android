package com.secondpasslibrary.reader.reader.readium

import androidx.compose.ui.graphics.toArgb
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearance
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearanceController
import com.secondpasslibrary.reader.reader.appearance.ReaderLayoutMode
import com.secondpasslibrary.reader.reader.appearance.ReaderTheme
import com.secondpasslibrary.reader.reader.appearance.readerPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.Spread
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi

/** Keeps app-owned appearance state and applies it to the currently bound Readium viewport. */
internal class ReadiumReaderAppearanceController(
    initialAppearance: ReaderAppearance = ReaderAppearance()
) : ReaderAppearanceController,
    AutoCloseable {
    private val lock = Any()
    private val updateMutex = Mutex()
    private val mutableAppearance = MutableStateFlow(initialAppearance)
    private var navigator: EpubNavigatorFragment? = null
    private var closed = false

    override val appearance = mutableAppearance.asStateFlow()

    override suspend fun update(appearance: ReaderAppearance) {
        updateMutex.withLock {
            mutableAppearance.value = appearance
            val boundNavigator = synchronized(lock) { if (closed) null else navigator }
            if (boundNavigator != null) {
                withContext(Dispatchers.Main.immediate) {
                    boundNavigator.submitPreferences(appearance.toReadiumPreferences())
                }
            }
        }
    }

    fun initialPreferences(): EpubPreferences = appearance.value.toReadiumPreferences()

    fun bind(value: EpubNavigatorFragment) = synchronized(lock) {
        check(!closed) { "Reader appearance controller is closed." }
        navigator = value
        value.submitPreferences(appearance.value.toReadiumPreferences())
    }

    fun unbind(value: EpubNavigatorFragment) = synchronized(lock) {
        if (navigator === value) navigator = null
    }

    override fun close() = synchronized(lock) {
        closed = true
        navigator = null
    }
}

@OptIn(ExperimentalReadiumApi::class)
internal fun ReaderAppearance.toReadiumPreferences(): EpubPreferences {
    val palette = theme.readerPalette()
    return EpubPreferences(
        backgroundColor = Color(palette.publicationBackground.toArgb()),
        textColor = Color(palette.publicationForeground.toArgb()),
        theme = when (theme) {
            ReaderTheme.LIGHT -> Theme.LIGHT
            ReaderTheme.DARK -> Theme.DARK
            ReaderTheme.SEPIA -> Theme.SEPIA
        },
        fontSize = fontScale,
        lineHeight = lineHeight,
        publisherStyles = publisherStylesEnabled,
        columnCount = when (layoutMode) {
            ReaderLayoutMode.SINGLE_COLUMN -> ColumnCount.ONE
            ReaderLayoutMode.AUTO -> ColumnCount.AUTO
            ReaderLayoutMode.TWO_COLUMN -> ColumnCount.TWO
        },
        // Readium 3.3.0 represents automatic spread selection with an unset preference.
        // EpubPreferences deliberately rejects Spread.AUTO.
        spread = when (layoutMode) {
            ReaderLayoutMode.SINGLE_COLUMN -> Spread.NEVER
            ReaderLayoutMode.AUTO -> null
            ReaderLayoutMode.TWO_COLUMN -> Spread.ALWAYS
        },
        scroll = false
    )
}
