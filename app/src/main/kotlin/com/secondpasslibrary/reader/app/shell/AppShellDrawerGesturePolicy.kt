package com.secondpasslibrary.reader.app.shell

/** Implemented by routes whose horizontal gestures must remain feature-owned. */
internal interface AppShellDrawerGesturePolicy {
    val drawerGestureEnabled: Boolean
}

internal val AppRoute.drawerGestureEnabled: Boolean
    get() = (this as? AppShellDrawerGesturePolicy)?.drawerGestureEnabled ?: true
