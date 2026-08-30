package com.secondpasslibrary.reader.reader.sync

import com.secondpasslibrary.reader.reader.annotations.mutation.ReaderAnnotationBatchWriter
import com.secondpasslibrary.reader.reader.annotations.mutation.SplReaderAnnotationWriter
import com.secondpasslibrary.reader.reader.progress.ReaderProgressWriter
import com.secondpasslibrary.reader.reader.progress.SplReaderProgressWriter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ReaderSyncModule {
    @Binds
    abstract fun bindReaderAnnotationBatchWriter(
        writer: SplReaderAnnotationWriter
    ): ReaderAnnotationBatchWriter

    @Binds
    abstract fun bindReaderProgressWriter(writer: SplReaderProgressWriter): ReaderProgressWriter
}
