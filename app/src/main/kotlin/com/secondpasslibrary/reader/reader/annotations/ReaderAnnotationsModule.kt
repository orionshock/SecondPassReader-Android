package com.secondpasslibrary.reader.reader.annotations

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ReaderAnnotationsModule {
    @Binds
    abstract fun bindReaderAnnotationsLoader(
        loader: SplReaderAnnotationsLoader
    ): ReaderAnnotationsLoader
}
