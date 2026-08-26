package com.secondpasslibrary.reader.reader.readium

import com.secondpasslibrary.reader.reader.appearance.ReaderAppearance
import com.secondpasslibrary.reader.reader.appearance.ReaderAppearanceController
import com.secondpasslibrary.reader.reader.appearance.ReaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
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
internal fun ReaderAppearance.toReadiumPreferences() = EpubPreferences(
    theme = when (theme) {
        ReaderTheme.LIGHT -> Theme.LIGHT
        ReaderTheme.DARK -> Theme.DARK
        ReaderTheme.SEPIA -> Theme.SEPIA
    },
    fontSize = fontScale,
    lineHeight = lineHeight,
    publisherStyles = publisherStylesEnabled,
    scroll = false
)
