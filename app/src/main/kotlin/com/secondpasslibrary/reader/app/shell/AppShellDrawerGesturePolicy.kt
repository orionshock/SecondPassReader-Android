package com.secondpasslibrary.reader.app.shell

import androidx.navigation3.runtime.NavKey

/** Implemented by a future route whose horizontal gestures must remain feature-owned. */
internal interface AppShellDrawerGesturePolicy {
    val drawerGestureEnabled: Boolean
}

internal val NavKey.drawerGestureEnabled: Boolean
    get() = (this as? AppShellDrawerGesturePolicy)?.drawerGestureEnabled ?: true
