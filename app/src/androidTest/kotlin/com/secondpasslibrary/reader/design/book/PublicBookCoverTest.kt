package com.secondpasslibrary.reader.design.book

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondpasslibrary.reader.design.SecondPassTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PublicBookCoverTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun retainedLocalCoverRendersWithoutRemoteReference() {
        val local = File(compose.activity.cacheDir, "offline-cover-test.png")
        val bitmap = Bitmap.createBitmap(60, 90, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.RED)
            local.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
        try {
            compose.setContent {
                SecondPassTheme {
                    PublicBookCover(
                        reference = null,
                        title = "Offline Book",
                        modifier = Modifier.size(120.dp, 180.dp),
                        localCover = local
                    )
                }
            }

            compose.waitUntil(timeoutMillis = 10_000) {
                runCatching {
                    val image = compose.onNodeWithContentDescription("Cover of Offline Book")
                        .captureToImage().toPixelMap()
                    val center = image[image.width / 2, image.height / 2]
                    center.red > 0.8f && center.green < 0.2f && center.blue < 0.2f
                }.getOrDefault(false)
            }
        } finally {
            local.delete()
        }
    }
}
