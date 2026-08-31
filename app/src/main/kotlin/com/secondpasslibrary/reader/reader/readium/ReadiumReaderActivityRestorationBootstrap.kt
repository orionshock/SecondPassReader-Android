package com.secondpasslibrary.reader.reader.readium

import androidx.fragment.app.FragmentActivity
import com.secondpasslibrary.reader.reader.lifecycle.ReaderActivityRestorationBootstrap
import javax.inject.Inject
import javax.inject.Singleton
import org.readium.r2.navigator.epub.EpubNavigatorFragment

internal const val READIUM_NAVIGATOR_TAG = "reader.epub.navigator"

/** Contains the Readium fragment workaround required by Android Activity restoration. */
@Singleton
internal class ReadiumReaderActivityRestorationBootstrap @Inject constructor() :
    ReaderActivityRestorationBootstrap {
    override fun prepareBeforeActivityRestore(activity: FragmentActivity) {
        activity.supportFragmentManager.fragmentFactory =
            EpubNavigatorFragment.createDummyFactory()
    }

    override fun completeAfterActivityRestore(activity: FragmentActivity) {
        activity.supportFragmentManager
            .findFragmentByTag(READIUM_NAVIGATOR_TAG)
            ?.let { restored ->
                activity.supportFragmentManager.beginTransaction()
                    .remove(restored)
                    .commitNowAllowingStateLoss()
            }
    }
}
