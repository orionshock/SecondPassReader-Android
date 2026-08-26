package com.secondpasslibrary.reader.reader.readium

import android.content.Context
import android.content.ContextWrapper
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import com.secondpasslibrary.reader.R
import com.secondpasslibrary.reader.reader.domain.ReaderViewport
import com.secondpasslibrary.reader.reader.readium.cfi.ReadiumCfiNavigatorBinding
import org.readium.r2.navigator.epub.EpubNavigatorFragment

private const val NAVIGATOR_TAG = "reader.epub.navigator"

internal fun FragmentActivity.installReaderEngineRestorationFactory() {
    supportFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
}

internal fun FragmentActivity.discardRestoredReaderViewport() {
    supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG)?.let { restored ->
        supportFragmentManager.beginTransaction()
            .remove(restored)
            .commitNowAllowingStateLoss()
    }
}

internal class ReadiumReaderViewport(
    private val fragmentFactory: ReadiumNavigatorFragmentFactory,
    private val cfiBinding: ReadiumCfiNavigatorBinding,
    private val publicationBinding: ReadiumPublicationNavigatorBinding,
    private val appearanceController: ReadiumReaderAppearanceController,
    private val movements: ReadiumViewportMovements,
    private val selectionEvents: ReadiumSelectionEvents,
    private val annotationDecorations: ReadiumReaderAnnotationDecorations
) : ReaderViewport {
    @Composable
    override fun Content(modifier: Modifier) {
        val activity = LocalContext.current.requireFragmentActivity()
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
        DisposableEffect(activity, this) {
            val fragments = activity.supportFragmentManager
            fragments.findFragmentByTag(NAVIGATOR_TAG)?.let { existing ->
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
                    NAVIGATOR_TAG
                )
                .commitNowAllowingStateLoss()
            val navigator = requireNotNull(
                fragments.findFragmentByTag(NAVIGATOR_TAG) as? EpubNavigatorFragment
            )
            cfiBinding.bind(navigator)
            publicationBinding.bind(navigator)
            appearanceController.bind(navigator)
            movements.bind(navigator)
            selectionEvents.bind(navigator)
            annotationDecorations.bind(navigator)
            onDispose {
                annotationDecorations.unbind(navigator)
                selectionEvents.unbind(navigator)
                movements.unbind(navigator)
                publicationBinding.unbind(navigator)
                appearanceController.unbind(navigator)
                cfiBinding.unbind(navigator)
                fragments.findFragmentByTag(NAVIGATOR_TAG)?.let { navigator ->
                    fragments.beginTransaction().remove(navigator).commitNowAllowingStateLoss()
                }
                fragments.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
            }
        }
    }
}

private tailrec fun Context.requireFragmentActivity(): FragmentActivity = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.requireFragmentActivity()
    else -> error("Reader requires a FragmentActivity host.")
}
