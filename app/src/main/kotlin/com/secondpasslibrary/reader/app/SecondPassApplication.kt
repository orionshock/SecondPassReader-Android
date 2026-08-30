package com.secondpasslibrary.reader.app

import android.app.Application
import androidx.work.Configuration
import com.secondpasslibrary.reader.reader.sync.ReaderSyncWorkerFactory
import com.secondpasslibrary.reader.reader.sync.readerWorkManagerConfiguration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SecondPassApplication :
    Application(),
    Configuration.Provider {
    @Inject
    internal lateinit var readerSyncWorkerFactory: ReaderSyncWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = readerWorkManagerConfiguration(readerSyncWorkerFactory)
}
