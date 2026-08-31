package com.secondpasslibrary.reader.reader.readium.viewport

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import com.secondpasslibrary.reader.R
import com.secondpasslibrary.reader.reader.domain.ReaderViewport
import com.secondpasslibrary.reader.reader.lifecycle.ReaderPositionRetentionController
import com.secondpasslibrary.reader.reader.readium.ReadiumNavigatorFragmentFactory
import com.secondpasslibrary.reader.reader.readium.ReadiumReaderAppearanceController
import com.secondpasslibrary.reader.reader.readium.annotations.ReadiumReaderAnnotationDecorations
import com.secondpasslibrary.reader.reader.readium.annotations.ReadiumSelectionEvents
import com.secondpasslibrary.reader.reader.readium.cfi.ReadiumCfiNavigatorBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubNavigatorFragment

internal const val READIUM_NAVIGATOR_TAG = "reader.epub.navigator"

internal class ReadiumReaderViewport(
    private val fragmentFactory: ReadiumNavigatorFragmentFactory,
    private val cfiBinding: ReadiumCfiNavigatorBinding,
    private val publicationBinding: ReadiumPublicationNavigatorBinding,
    private val appearanceController: ReadiumReaderAppearanceController,
    private val movements: ReadiumViewportMovements,
    private val hudEvents: ReadiumReaderHudEvents,
    private val selectionEvents: ReadiumSelectionEvents,
    private val annotationDecorations: ReadiumReaderAnnotationDecorations,
    private val positionRetention: ReaderPositionRetentionController
) : ReaderViewport {
    @Composable
    override fun Content(modifier: Modifier) {
        val activity = LocalContext.current.requireFragmentActivity()
        val viewportScope = rememberCoroutineScope()
        ReaderNavigatorContainer(modifier)
        DisposableEffect(activity, this) {
            val prePauseCapture = PrePauseCapture(activity) {
                positionRetention.captureBeforeNavigatorLoss()
            }
            activity.application.registerActivityLifecycleCallbacks(prePauseCapture)
            val navigator = installNavigator(activity)
            var attached = false
            var retentionAttachment: Long? = null
            val attachJob = viewportScope.launch(start = CoroutineStart.UNDISPATCHED) {
                positionRetention.awaitPendingCapture()
                cfiBinding.bind(navigator)
                publicationBinding.bind(navigator)
                appearanceController.bind(navigator)
                movements.bind(navigator)
                hudEvents.bind(navigator)
                selectionEvents.bind(navigator)
                annotationDecorations.bind(navigator)
                attached = true
                retentionAttachment = positionRetention.navigatorAttached()
            }
            onDispose {
                activity.application.unregisterActivityLifecycleCallbacks(prePauseCapture)
                attachJob.cancel()
                retentionAttachment?.let(positionRetention::navigatorDetached)
                CoroutineScope(Dispatchers.Main.immediate).launch(
                    start = CoroutineStart.UNDISPATCHED
                ) {
                    positionRetention.awaitPendingCapture()
                    if (attached) {
                        annotationDecorations.unbind(navigator)
                        selectionEvents.unbind(navigator)
                        hudEvents.unbind(navigator)
                        movements.unbind(navigator)
                        publicationBinding.unbind(navigator)
                        appearanceController.unbind(navigator)
                    }
                    val fragments = activity.supportFragmentManager
                    fragments.findFragmentByTag(READIUM_NAVIGATOR_TAG)?.let { navigator ->
                        fragments.beginTransaction().remove(navigator)
                            .commitNowAllowingStateLoss()
                    }
                    fragments.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
                }
            }
        }
    }

    private fun installNavigator(activity: FragmentActivity): EpubNavigatorFragment {
        val fragments = activity.supportFragmentManager
        fragments.findFragmentByTag(READIUM_NAVIGATOR_TAG)?.let { existing ->
            (existing as? EpubNavigatorFragment)?.let(cfiBinding::unbind)
            (existing as? EpubNavigatorFragment)?.let(appearanceController::unbind)
            fragments.beginTransaction().remove(existing).commitNowAllowingStateLoss()
        }
        fragments.fragmentFactory = fragmentFactory.create()
        fragments.beginTransaction()
            .replace(
                R.id.reader_navigator_container,
                EpubNavigatorFragment::class.java,
                null,
                READIUM_NAVIGATOR_TAG
            )
            .commitNowAllowingStateLoss()
        return requireNotNull(
            fragments.findFragmentByTag(READIUM_NAVIGATOR_TAG) as? EpubNavigatorFragment
        )
    }
}

@Composable
private fun ReaderNavigatorContainer(modifier: Modifier) {
    AndroidView(
        factory = { context ->
            FragmentContainerView(context).apply {
                id = R.id.reader_navigator_container
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = modifier.fillMaxSize()
    )
}

private class PrePauseCapture(
    private val observedActivity: Activity,
    private val capture: () -> Unit
) : Application.ActivityLifecycleCallbacks {
    override fun onActivityPrePaused(activity: Activity) {
        if (activity === observedActivity) capture()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}

private tailrec fun Context.requireFragmentActivity(): FragmentActivity = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.requireFragmentActivity()
    else -> error("Reader requires a FragmentActivity host.")
}
