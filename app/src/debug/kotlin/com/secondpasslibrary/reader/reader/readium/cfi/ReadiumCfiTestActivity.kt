package com.secondpasslibrary.reader.reader.readium.cfi

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.secondpasslibrary.reader.app.readerActivityRestorationBootstrap
import com.secondpasslibrary.reader.design.SecondPassTheme
import com.secondpasslibrary.reader.reader.domain.ReaderEngine
import com.secondpasslibrary.reader.reader.lifecycle.restoreActivity
import com.secondpasslibrary.reader.reader.readium.ReadiumReaderEngineOpener
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.readium.r2.navigator.epub.EpubNavigatorFragment

private const val TEST_ENGINE_OPEN_TIMEOUT_MILLIS = 20_000L

/** Debug-only lifecycle host for connected Readium adapter tests. */
internal class ReadiumCfiTestActivity : FragmentActivity() {
    private val model by viewModels<ReadiumCfiTestViewModel> {
        ReadiumCfiTestViewModel.Factory(
            applicationContext,
            requireNotNull(intent.getStringExtra(EXTRA_EPUB_PATH))
        )
    }
    private val mutableNavigatorGeneration = MutableStateFlow(0)
    private val mutableViewportAttached = MutableStateFlow(false)
    val hostState get() = model.state
    val navigatorGeneration = mutableNavigatorGeneration.asStateFlow()

    private val fragmentLifecycle = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentResumed(fragmentManager: FragmentManager, fragment: Fragment) {
            if (fragment is EpubNavigatorFragment) {
                mutableNavigatorGeneration.value += 1
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applicationContext.readerActivityRestorationBootstrap().restoreActivity(this) {
            super.onCreate(savedInstanceState)
        }
        mutableViewportAttached.value = !intent.getBooleanExtra(EXTRA_DEFER_VIEWPORT, false)
        supportFragmentManager.registerFragmentLifecycleCallbacks(fragmentLifecycle, false)
        setContent {
            SecondPassTheme {
                val state by model.state.collectAsStateWithLifecycle()
                val viewportAttached by mutableViewportAttached.collectAsStateWithLifecycle()
                when (val current = state) {
                    ReadiumCfiTestHostState.Loading -> Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        CircularProgressIndicator()
                    }

                    is ReadiumCfiTestHostState.Ready -> if (viewportAttached) {
                        current.engine.viewport.Content(Modifier.fillMaxSize())
                    }

                    is ReadiumCfiTestHostState.Failed -> Unit
                }
            }
        }
    }

    override fun onDestroy() {
        supportFragmentManager.unregisterFragmentLifecycleCallbacks(fragmentLifecycle)
        super.onDestroy()
    }

    fun currentNavigator(): EpubNavigatorFragment? =
        supportFragmentManager.fragments.filterIsInstance<EpubNavigatorFragment>().singleOrNull()

    fun attachViewport() {
        mutableViewportAttached.value = true
    }

    internal companion object {
        const val EXTRA_EPUB_PATH = "reader.cfi.test.EPUB_PATH"
        const val EXTRA_DEFER_VIEWPORT = "reader.cfi.test.DEFER_VIEWPORT"
    }
}

internal sealed interface ReadiumCfiTestHostState {
    data object Loading : ReadiumCfiTestHostState

    data class Ready(val engine: ReaderEngine) : ReadiumCfiTestHostState

    data class Failed(val reason: String) : ReadiumCfiTestHostState
}

private class ReadiumCfiTestViewModel(context: Context, epubPath: String) : ViewModel() {
    private val mutableState = MutableStateFlow<ReadiumCfiTestHostState>(
        ReadiumCfiTestHostState.Loading
    )
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            mutableState.value = runCatching {
                withTimeout(TEST_ENGINE_OPEN_TIMEOUT_MILLIS) {
                    ReadiumReaderEngineOpener(context).open(File(epubPath))
                }
            }.fold(
                onSuccess = ReadiumCfiTestHostState::Ready,
                onFailure = {
                    ReadiumCfiTestHostState.Failed(it.boundedFailureChain())
                }
            )
        }
    }

    override fun onCleared() {
        (state.value as? ReadiumCfiTestHostState.Ready)?.engine?.close()
    }

    internal class Factory(private val context: Context, private val epubPath: String) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == ReadiumCfiTestViewModel::class.java)
            return ReadiumCfiTestViewModel(context, epubPath) as T
        }
    }
}

private fun Throwable.boundedFailureChain(): String = generateSequence(this) { it.cause }
    .take(5)
    .joinToString(" -> ") { failure ->
        val message = failure.message?.replace(Regex("\\s+"), " ")?.take(160)
        listOfNotNull(failure::class.java.simpleName, message).joinToString(": ")
    }
