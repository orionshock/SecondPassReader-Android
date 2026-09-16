package com.secondpasslibrary.reader.reader.asset

import java.io.File
import java.io.IOException
import java.security.MessageDigest

internal class ReaderEpubIntegrityException :
    IOException(
        "The downloaded EPUB could not be verified."
    )

@JvmInline
internal value class ReaderBookAssetChecksum private constructor(val value: String) {
    fun matches(file: File): Boolean {
        if (!file.isFile || file.length() == 0L) return false
        val digest = MessageDigest.getInstance(ALGORITHM)
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        return actual.equals(value, ignoreCase = true)
    }

    companion object {
        private const val ALGORITHM = "SHA-256"
        private val HEX_SHA_256 = Regex("^[0-9a-fA-F]{64}$")

        fun fromServer(value: String?): ReaderBookAssetChecksum {
            if (value == null || !HEX_SHA_256.matches(value)) {
                throw ReaderEpubIntegrityException()
            }
            return ReaderBookAssetChecksum(value)
        }
    }
}
