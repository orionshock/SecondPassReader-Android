package com.secondpasslibrary.reader.reader

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
import com.secondpasslibrary.reader.reader.publication.ReaderPublication
import org.readium.r2.navigator.epub.EpubNavigatorFragment

private const val NAVIGATOR_TAG = "reader.epub.navigator"

internal fun FragmentActivity.installReaderRestorationFactory() {
    supportFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
}

internal fun FragmentActivity.discardRestoredReaderNavigator() {
    supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG)?.let { restored ->
        supportFragmentManager.beginTransaction()
            .remove(restored)
            .commitNowAllowingStateLoss()
    }
}

@Composable
internal fun ReaderNavigatorHost(publication: ReaderPublication, modifier: Modifier = Modifier) {
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
    DisposableEffect(activity, publication) {
        val fragments = activity.supportFragmentManager
        fragments.findFragmentByTag(NAVIGATOR_TAG)?.let { existing ->
            fragments.beginTransaction().remove(existing).commitNowAllowingStateLoss()
        }
        fragments.fragmentFactory = publication.navigatorFragmentFactory()
        fragments.beginTransaction()
            .replace(
                R.id.reader_navigator_container,
                EpubNavigatorFragment::class.java,
                null,
                NAVIGATOR_TAG
            )
            .commitNowAllowingStateLoss()
        onDispose {
            fragments.findFragmentByTag(NAVIGATOR_TAG)?.let { navigator ->
                fragments.beginTransaction().remove(navigator).commitNowAllowingStateLoss()
            }
            fragments.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
        }
    }
}

private tailrec fun Context.requireFragmentActivity(): FragmentActivity = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.requireFragmentActivity()
    else -> error("Reader requires a FragmentActivity host.")
}
