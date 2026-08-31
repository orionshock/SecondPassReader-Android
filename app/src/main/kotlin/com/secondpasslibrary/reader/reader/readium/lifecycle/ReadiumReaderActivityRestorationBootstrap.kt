package com.secondpasslibrary.reader.reader.readium.lifecycle

import androidx.fragment.app.FragmentActivity
import com.secondpasslibrary.reader.reader.lifecycle.ReaderActivityRestorationBootstrap
import com.secondpasslibrary.reader.reader.readium.viewport.READIUM_NAVIGATOR_TAG
import javax.inject.Inject
import javax.inject.Singleton
import org.readium.r2.navigator.epub.EpubNavigatorFragment

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
