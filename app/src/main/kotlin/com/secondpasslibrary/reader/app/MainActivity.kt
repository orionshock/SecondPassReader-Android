package com.secondpasslibrary.reader.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.secondpasslibrary.reader.design.SecondPassTheme
import com.secondpasslibrary.reader.reader.lifecycle.restoreActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        applicationContext.readerActivityRestorationBootstrap().restoreActivity(this) {
            super.onCreate(savedInstanceState)
        }
        enableEdgeToEdge()
        setContent {
            SecondPassTheme {
                SecondPassApp()
            }
        }
    }
}
