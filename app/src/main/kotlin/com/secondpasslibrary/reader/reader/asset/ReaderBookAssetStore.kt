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

internal data class ReaderCompletedBookMetadata(
    val bookId: String,
    val title: String,
    val checksum: ReaderBookAssetChecksum
)

@Singleton
internal class ReaderBookAssetStore private constructor(private val root: File) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(File(context.filesDir, "reader/books"))

    private val writes = Mutex()

    suspend fun findCompleted(account: ReaderAccountScope, bookId: String): ReaderBookAsset? =
        withContext(Dispatchers.IO) {
            require(bookId.isNotBlank()) { "Book ID must not be blank." }
            writes.withLock {
                val metadata = readMetadata(metadataFile(account, bookId))
                    ?.takeIf { it.bookId == bookId }
                if (metadata == null) {
                    completedFile(account, bookId).delete()
                    metadataFile(account, bookId).delete()
                    return@withLock null
                }
                verifiedCompleted(account, bookId, metadata.checksum)
            }
        }

    suspend fun acquire(
        account: ReaderAccountScope,
        bookId: String,
        checksum: ReaderBookAssetChecksum,
        onDownloadStarted: () -> Unit,
        download: suspend (OutputStream) -> Unit
    ): ReaderBookAsset = withContext(Dispatchers.IO) {
        require(bookId.isNotBlank()) { "Book ID must not be blank." }
        writes.withLock {
            val destination = completedFile(account, bookId)
            verifiedCompleted(account, bookId, checksum)?.let { return@withLock it }
            destination.parentFile?.mkdirs()
            val partial = File(destination.parentFile, "${destination.name}.part")
            partial.delete()
            try {
                onDownloadStarted()
                partial.outputStream().buffered().use { output -> download(output) }
                if (!checksum.matches(partial)) throw ReaderEpubIntegrityException()
                moveCompleted(partial, destination)
                ReaderBookAsset(destination, reused = false)
            } finally {
                partial.delete()
            }
        }
    }

    suspend fun rememberCompletedBook(
        account: ReaderAccountScope,
        bookId: String,
        title: String,
        checksum: ReaderBookAssetChecksum
    ) = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext
        writes.withLock {
            if (verifiedCompleted(account, bookId, checksum) == null) return@withLock
            val destination = metadataFile(account, bookId)
            destination.parentFile?.mkdirs()
            destination.writeText(
                listOf(bookId, title, checksum.value).joinToString("\n") { value ->
                    Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
                }
            )
        }
    }

    suspend fun completedBooks(account: ReaderAccountScope): List<ReaderCompletedBookMetadata> =
        withContext(Dispatchers.IO) {
            writes.withLock {
                accountDirectory(account).listFiles { file -> file.extension == "metadata" }
                    .orEmpty()
                    .mapNotNull { file ->
                        readMetadata(file)?.takeIf {
                            metadataFile(account, it.bookId) == file
                        }
                    }
                    .filter { verifiedCompleted(account, it.bookId, it.checksum) != null }
            }
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
            title = values[1].takeIf(String::isNotBlank) ?: return@runCatching null,
            checksum = ReaderBookAssetChecksum.fromServer(values.getOrNull(2))
        )
    }.getOrNull()

    private fun verifiedCompleted(
        account: ReaderAccountScope,
        bookId: String,
        checksum: ReaderBookAssetChecksum
    ): ReaderBookAsset? {
        val completed = completedFile(account, bookId)
        if (checksum.matches(completed)) return ReaderBookAsset(completed, reused = true)
        completed.delete()
        metadataFile(account, bookId).delete()
        return null
    }

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
