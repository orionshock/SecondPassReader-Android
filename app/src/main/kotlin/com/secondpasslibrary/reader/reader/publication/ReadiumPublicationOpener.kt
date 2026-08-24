package com.secondpasslibrary.reader.reader.publication

import android.content.Context
import androidx.fragment.app.FragmentFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

internal class ReaderPublicationOpenException(message: String) : Exception(message)

internal interface ReaderPublication : AutoCloseable {
    fun navigatorFragmentFactory(): FragmentFactory
}

internal fun interface ReaderPublicationOpener {
    suspend fun open(file: File): ReaderPublication
}

private class ReadiumPublication internal constructor(private val publication: Publication) :
    ReaderPublication {
    override fun navigatorFragmentFactory(): FragmentFactory =
        EpubNavigatorFactory(publication).createFragmentFactory(initialLocator = null)

    override fun close() = publication.close()
}

@Singleton
internal class ReadiumPublicationOpener @Inject constructor(@ApplicationContext context: Context) :
    ReaderPublicationOpener {
    private val httpClient = DefaultHttpClient()
    private val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
    private val publicationOpener =
        PublicationOpener(
            DefaultPublicationParser(context, httpClient, assetRetriever, pdfFactory = null)
        )

    override suspend fun open(file: File): ReaderPublication = withContext(Dispatchers.IO) {
        val asset = assetRetriever.retrieve(file).getOrNull()
            ?: throw ReaderPublicationOpenException("The EPUB asset could not be read.")
        val publication = publicationOpener.open(asset, allowUserInteraction = false).getOrNull()
            ?: throw ReaderPublicationOpenException("The EPUB could not be opened.")
        if (!publication.conformsTo(Publication.Profile.EPUB)) {
            publication.close()
            throw ReaderPublicationOpenException("The Book asset is not an EPUB.")
        }
        ReadiumPublication(publication)
    }
}
