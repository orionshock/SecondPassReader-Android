package com.secondpasslibrary.reader.app

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject

/** Installed package identity shared by About and the pairing client type. */
class AppIdentity @Inject constructor(@ApplicationContext private val context: Context) {
    val packageId: String get() = context.packageName
    val versionName: String
        get() = context.packageManager.getPackageInfo(packageId, 0).versionName.orEmpty()
    val versionCode: Long
        get() = context.packageManager.getPackageInfo(packageId, 0).longVersionCode
    val device: String
        get() = listOf(
            Build.MANUFACTURER.replaceFirstChar { it.titlecase(Locale.ROOT) },
            Build.MODEL
        ).filter(String::isNotBlank)
            .distinct().joinToString(" ")
    val androidVersion: String get() = Build.VERSION.RELEASE
    val apiLevel: Int get() = Build.VERSION.SDK_INT
    val pairingClientType: String get() = pairingClientType(versionName)
}

internal fun pairingClientType(versionName: String) = "SPR-Android-$versionName"
