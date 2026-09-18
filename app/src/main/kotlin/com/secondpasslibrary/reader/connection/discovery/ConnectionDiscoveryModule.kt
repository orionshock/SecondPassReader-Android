package com.secondpasslibrary.reader.connection.discovery

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object ConnectionDiscoveryModule {
    @Provides
    @Singleton
    fun provideLanLibraryUrlDiscovery(
        @ApplicationContext context: Context
    ): LanLibraryUrlDiscovery = AndroidNsdLibraryUrlDiscoveryAdapter(AndroidNsdBrowser(context))
}
