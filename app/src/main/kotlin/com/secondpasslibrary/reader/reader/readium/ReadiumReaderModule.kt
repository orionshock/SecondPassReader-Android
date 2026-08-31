package com.secondpasslibrary.reader.reader.readium

import com.secondpasslibrary.reader.reader.domain.ReaderEngineOpener
import com.secondpasslibrary.reader.reader.lifecycle.ReaderActivityRestorationBootstrap
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ReadiumReaderModule {
    @Binds
    abstract fun bindReaderActivityRestorationBootstrap(
        bootstrap: ReadiumReaderActivityRestorationBootstrap
    ): ReaderActivityRestorationBootstrap

    @Binds
    abstract fun bindReaderEngineOpener(opener: ReadiumReaderEngineOpener): ReaderEngineOpener
}
