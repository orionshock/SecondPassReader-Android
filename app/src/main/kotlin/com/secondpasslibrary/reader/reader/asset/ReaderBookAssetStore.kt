package com.secondpasslibrary.reader.reader.asset

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class ReaderAccountScope(val serverOrigin: String, val profileId: String) {
    init {
        require(serverOrigin.isNotBlank()) { "Server origin must not be blank." }
        require(profileId.isNotBlank()) { "Profile ID must not be blank." }
    }
}

internal data class ReaderBookAsset(val file: File, val reused: Boolean)

internal data class ReaderCompletedBookMetadata(val bookId: String, val title: String)

@Singleton
internal class ReaderBookAssetStore private constructor(private val root: File) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(File(context.filesDir, "reader/books"))

    private val writes = Mutex()

    suspend fun findCompleted(account: ReaderAccountScope, bookId: String): ReaderBookAsset? =
        withContext(Dispatchers.IO) {
            require(bookId.isNotBlank()) { "Book ID must not be blank." }
            completedFile(account, bookId)
                .takeIf { it.isFile && it.length() > 0L }
                ?.let { ReaderBookAsset(it, reused = true) }
        }

    suspend fun acquire(
        account: ReaderAccountScope,
        bookId: String,
        onDownloadStarted: () -> Unit,
        download: suspend (OutputStream) -> Unit
    ): ReaderBookAsset = withContext(Dispatchers.IO) {
        require(bookId.isNotBlank()) { "Book ID must not be blank." }
        writes.withLock {
            val destination = completedFile(account, bookId)
            if (destination.isFile && destination.length() > 0L) {
                return@withLock ReaderBookAsset(destination, reused = true)
            }
            destination.parentFile?.mkdirs()
            val partial = File(destination.parentFile, "${destination.name}.part")
            partial.delete()
            try {
                onDownloadStarted()
                partial.outputStream().buffered().use { output -> download(output) }
                check(partial.length() > 0L) { "The downloaded EPUB is empty." }
                moveCompleted(partial, destination)
                ReaderBookAsset(destination, reused = false)
            } finally {
                partial.delete()
            }
        }
    }

    suspend fun rememberCompletedBook(account: ReaderAccountScope, bookId: String, title: String) =
        withContext(Dispatchers.IO) {
            if (title.isBlank() || findCompleted(account, bookId) == null) return@withContext
            writes.withLock {
                val destination = metadataFile(account, bookId)
                destination.parentFile?.mkdirs()
                destination.writeText(
                    listOf(bookId, title).joinToString("\n") { value ->
                        Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
                    }
                )
            }
        }

    suspend fun completedBooks(account: ReaderAccountScope): List<ReaderCompletedBookMetadata> =
        withContext(Dispatchers.IO) {
            accountDirectory(account).listFiles { file -> file.extension == "metadata" }
                .orEmpty()
                .mapNotNull { file -> readMetadata(file) }
                .filter { findCompleted(account, it.bookId) != null }
        }

    internal fun completedFile(account: ReaderAccountScope, bookId: String): File = File(
        accountDirectory(account),
        "${digest(bookId)}.epub"
    )

    private fun metadataFile(account: ReaderAccountScope, bookId: String): File = File(
        accountDirectory(account),
        "${digest(bookId)}.metadata"
    )

    private fun accountDirectory(account: ReaderAccountScope) =
        File(root, digest("${account.serverOrigin}\u0000${account.profileId}"))

    private fun readMetadata(file: File): ReaderCompletedBookMetadata? = runCatching {
        val values = file.readLines().map { encoded ->
            Base64.getDecoder().decode(encoded).toString(Charsets.UTF_8)
        }
        ReaderCompletedBookMetadata(
            bookId = values[0].takeIf(String::isNotBlank) ?: return@runCatching null,
            title = values[1].takeIf(String::isNotBlank) ?: return@runCatching null
        )
    }.getOrNull()

    private fun moveCompleted(partial: File, destination: File) {
        try {
            Files.move(
                partial.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(partial.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    internal companion object {
        fun forTests(root: File): ReaderBookAssetStore = ReaderBookAssetStore(root)
    }
}
