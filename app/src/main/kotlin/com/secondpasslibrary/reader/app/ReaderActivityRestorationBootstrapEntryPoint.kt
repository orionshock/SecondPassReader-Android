package com.secondpasslibrary.reader.app

import android.content.Context
import com.secondpasslibrary.reader.reader.lifecycle.ReaderActivityRestorationBootstrap
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Application entry point because Activity field injection occurs after [android.app.Activity.onCreate]. */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ReaderActivityRestorationBootstrapEntryPoint {
    fun readerActivityRestorationBootstrap(): ReaderActivityRestorationBootstrap
}

internal fun Context.readerActivityRestorationBootstrap(): ReaderActivityRestorationBootstrap =
    EntryPointAccessors.fromApplication(
        applicationContext,
        ReaderActivityRestorationBootstrapEntryPoint::class.java
    ).readerActivityRestorationBootstrap()
