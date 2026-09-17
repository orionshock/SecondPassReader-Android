package com.secondpasslibrary.reader.reader

import com.secondpasslibrary.reader.reader.asset.ReaderBookAssetResolver
import com.secondpasslibrary.reader.reader.asset.SplReaderBookAssetResolver
import com.secondpasslibrary.reader.reader.marginalia.ReaderMarginaliaLayerHistoryLoader
import com.secondpasslibrary.reader.reader.marginalia.SplReaderMarginaliaLayerHistoryLoader
import com.secondpasslibrary.reader.reader.session.ReaderSessionMetadataWriter
import com.secondpasslibrary.reader.reader.session.SplReaderSessionMetadataWriter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ReaderViewModelModule {
    @Binds
    abstract fun bindReaderBookAssetResolver(
        resolver: SplReaderBookAssetResolver
    ): ReaderBookAssetResolver

    @Binds
    abstract fun bindReaderLaunchAdmission(policy: ReaderLaunchPolicy): ReaderLaunchAdmission

    @Binds
    abstract fun bindReaderMarginaliaLayerHistoryLoader(
        loader: SplReaderMarginaliaLayerHistoryLoader
    ): ReaderMarginaliaLayerHistoryLoader

    @Binds
    abstract fun bindReaderSessionMetadataWriter(
        writer: SplReaderSessionMetadataWriter
    ): ReaderSessionMetadataWriter
}
