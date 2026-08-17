package com.secondpasslibrary.reader.connection

import android.os.Build
import java.util.Locale
import javax.inject.Inject

class AndroidClientNameProvider
@Inject
constructor() {
    fun defaultName(): String {
        val model =
            Build.MODEL
                .trim()
                .replace('_', ' ')
                .split(Regex("\\s+"))
                .joinToString(" ") { word ->
                    word.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
                }.takeIf(String::isNotBlank)
                ?: "Android"
        return "Second Pass Reader · $model".take(MAX_CLIENT_NAME_LENGTH)
    }

    private companion object {
        const val MAX_CLIENT_NAME_LENGTH = 200
    }
}
