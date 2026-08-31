package com.secondpasslibrary.reader.reader.lifecycle

import androidx.fragment.app.FragmentActivity

/** Prepares renderer restoration before Android restores an Activity's fragments. */
internal interface ReaderActivityRestorationBootstrap {
    fun prepareBeforeActivityRestore(activity: FragmentActivity)

    fun completeAfterActivityRestore(activity: FragmentActivity)
}

internal inline fun ReaderActivityRestorationBootstrap.restoreActivity(
    activity: FragmentActivity,
    restore: () -> Unit
) {
    runReaderActivityRestore(
        prepare = { prepareBeforeActivityRestore(activity) },
        restore = restore,
        complete = { completeAfterActivityRestore(activity) }
    )
}

internal inline fun runReaderActivityRestore(
    prepare: () -> Unit,
    restore: () -> Unit,
    complete: () -> Unit
) {
    prepare()
    restore()
    complete()
}
