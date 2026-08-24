package com.secondpasslibrary.reader.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.secondpasslibrary.reader.design.SecondPassTheme
import com.secondpasslibrary.reader.reader.discardRestoredReaderNavigator
import com.secondpasslibrary.reader.reader.installReaderRestorationFactory
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installReaderRestorationFactory()
        super.onCreate(savedInstanceState)
        discardRestoredReaderNavigator()
        enableEdgeToEdge()
        setContent {
            SecondPassTheme {
                SecondPassApp()
            }
        }
    }
}
