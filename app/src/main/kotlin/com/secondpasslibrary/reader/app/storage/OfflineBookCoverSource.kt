package com.secondpasslibrary.reader.app.storage

import android.content.Context
import android.graphics.Bitmap
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.secondpasslibrary.client.PublicBookCoverReference
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

internal fun interface OfflineBookCoverSource {
    suspend fun fetch(reference: PublicBookCoverReference): ByteArray
}

/** Retains what the existing Coil image path actually renders, independent of its evictable cache. */
internal class CoilOfflineBookCoverAdapter @Inject constructor(
    @ApplicationContext private val context: Context
) : OfflineBookCoverSource {
    override suspend fun fetch(reference: PublicBookCoverReference): ByteArray {
        val request = ImageRequest.Builder(context)
            .data(reference.url)
            .size(600, 900)
            .allowHardware(false)
            .build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult
            ?: throw IllegalStateException("Book cover could not be loaded.")
        return ByteArrayOutputStream().use { output ->
            check(
                result.image.toBitmap().compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, output)
            ) {
                "Book cover could not be retained."
            }
            output.toByteArray()
        }
    }

    private companion object {
        const val PNG_QUALITY = 100
    }
}
