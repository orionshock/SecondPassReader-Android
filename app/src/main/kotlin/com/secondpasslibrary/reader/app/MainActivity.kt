package com.secondpasslibrary.reader.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.secondpasslibrary.reader.design.SecondPassTheme
import com.secondpasslibrary.reader.reader.readium.discardRestoredReaderViewport
import com.secondpasslibrary.reader.reader.readium.installReaderEngineRestorationFactory
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installReaderEngineRestorationFactory()
        super.onCreate(savedInstanceState)
        discardRestoredReaderViewport()
        enableEdgeToEdge()
        setContent {
            SecondPassTheme {
                SecondPassApp()
            }
        }
    }
}
