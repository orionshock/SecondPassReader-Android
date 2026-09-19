package com.secondpasslibrary.reader.reader.asset

import com.secondpasslibrary.client.PublicBookCoverReference
import com.secondpasslibrary.reader.connection.AuthenticatedClientProvider
import com.secondpasslibrary.reader.connection.ConnectionProfile
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

internal class ReaderEpubUnavailableException : Exception("The Book has no EPUB asset.")

internal data class ResolvedReaderBook(
    val title: String,
    val file: File,
    val reused: Boolean,
    val cover: PublicBookCoverReference? = null
)

internal fun interface ReaderBookAssetResolver {
    suspend fun resolve(
        request: ReaderBookAssetRequest,
        onDownloadStarted: () -> Unit
    ): ResolvedReaderBook
}

internal data class ReaderBookAssetRequest(
    val profile: ConnectionProfile,
    val profileId: String,
    val bookId: String,
    val titleHint: String? = null,
    val localOnly: Boolean = false
)

@Singleton
internal class SplReaderBookAssetResolver @Inject constructor(
    private val clientProvider: AuthenticatedClientProvider,
    private val assetStore: ReaderBookAssetStore
) : ReaderBookAssetResolver {
    override suspend fun resolve(
        request: ReaderBookAssetRequest,
        onDownloadStarted: () -> Unit
    ): ResolvedReaderBook {
        val account = ReaderAccountScope(request.profile.serverOrigin, request.profileId)
        if (request.localOnly) {
            val local = assetStore.findCompleted(account, request.bookId)
                ?: throw ReaderEpubUnavailableException()
            return ResolvedReaderBook(
                request.titleHint?.takeIf(String::isNotBlank) ?: "Downloaded book",
                local.file,
                reused = true
            )
        }
        val client = clientProvider.forProfile(request.profile)
        val book = client.library.books.getBook(request.bookId)
        val file = book.file?.takeIf { it.format.lowercase(Locale.ROOT) == "epub" }
            ?: throw ReaderEpubUnavailableException()
        val checksum = ReaderBookAssetChecksum.fromServer(file.checksum)
        val asset = assetStore.acquire(
            account,
            request.bookId,
            checksum,
            onDownloadStarted
        ) { output -> client.library.books.downloadBook(file.download, output) }
        assetStore.rememberCompletedBook(account, request.bookId, book.title, checksum)
        return ResolvedReaderBook(book.title, asset.file, asset.reused, book.cover)
    }
}
